package com.groove.order.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.cart.entity.CartItem;
import com.groove.cart.repository.CartItemRepository;
import com.groove.coupon.entity.MemberCoupon;
import com.groove.coupon.repository.MemberCouponRepository;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.PageResponse;
import com.groove.limited.entity.LimitedDropStatus;
import com.groove.limited.repository.LimitedDropRepository;
import com.groove.limited.repository.LimitedPurchaseRepository;
import com.groove.limited.service.LimitedPurchaseWriter;
import com.groove.limited.service.LimitedRelease;
import com.groove.limited.service.LimitedReleaseSynchronizer;
import com.groove.member.entity.Address;
import com.groove.member.entity.Member;
import com.groove.member.repository.AddressRepository;
import com.groove.member.repository.MemberRepository;
import com.groove.order.dto.OrderCancelRequest;
import com.groove.order.dto.OrderCreateRequest;
import com.groove.order.dto.OrderCreateResponse;
import com.groove.order.dto.OrderDetailResponse;
import com.groove.order.dto.OrderPaymentResponse;
import com.groove.order.dto.OrderSearchCondition;
import com.groove.order.dto.OrderSearchRequest;
import com.groove.order.dto.OrderSummaryResponse;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderStatus;
import com.groove.order.entity.ShippingAddress;
import com.groove.order.mapper.OrderQueryMapper;
import com.groove.order.repository.OrderRepository;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentRepository;
import com.groove.product.entity.Product;
import com.groove.product.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

/** 주문 생성·조회·취소. 생성은 장바구니 항목 또는 단일 상품 중 하나를 원천으로 재고를 차감하고 PENDING 주문을 만든다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrderService {

	private final MemberRepository memberRepository;
	private final AddressRepository addressRepository;
	private final ProductRepository productRepository;
	private final LimitedDropRepository limitedDropRepository;
	private final LimitedPurchaseRepository limitedPurchaseRepository;
	private final LimitedPurchaseWriter limitedPurchaseWriter;
	private final LimitedReleaseSynchronizer limitedReleaseSynchronizer;
	private final CartItemRepository cartItemRepository;
	private final MemberCouponRepository memberCouponRepository;
	private final OrderStockService orderStockService;
	private final OrderRepository orderRepository;
	private final OrderNumberGenerator orderNumberGenerator;
	private final OrderQueryMapper orderQueryMapper;
	private final PaymentRepository paymentRepository;
	private final PaymentCancelHook paymentCancelHook;
	private final Clock clock;

	@Transactional
	public OrderCreateResponse create(Long memberId, OrderCreateRequest request) {
		Member member = findActiveMember(memberId);
		Address address = addressRepository.findByIdAndMemberId(request.addressId(), memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_ADDRESS_NOT_FOUND));
		MemberCoupon memberCoupon = request.memberCouponId() == null ? null
				: memberCouponRepository.findWithCouponByIdAndMemberIdForUpdate(request.memberCouponId(), memberId)
						.orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));

		List<CartItem> cartItems = request.isFromCart()
				? findOwnedCartItems(memberId, request.cartItemIds())
				: List.of();
		List<OrderLine> lines = request.isFromCart()
				? resolveCartLines(cartItems)
				: resolveDirectLine(request.productId(), request.quantity());

		String orderNumber = orderNumberGenerator.generate();
		Order order = Order.create(orderNumber, member, ShippingAddress.from(address), LocalDateTime.now(clock));
		for (OrderLine line : lines) {
			order.addItem(line.product(), line.quantity());
		}
		if (memberCoupon != null) {
			order.applyCoupon(memberCoupon, memberCoupon.calculateDiscount(order.getTotalAmount()));
		}
		orderStockService.deduct(order);
		orderRepository.save(order);

		// 쿠폰 사용은 주문 생성과 원자적으로 성공/실패해야 하므로 이벤트 대신 같은 트랜잭션에서 직접 호출한다.
		// 비동기·별도 트랜잭션 이벤트는 재고 차감 후 쿠폰 실패 시 정합성 깨짐.
		if (memberCoupon != null) {
			memberCoupon.use(order.getId());
		}

		if (request.isFromCart()) {
			cartItemRepository.deleteAll(cartItems);
		}
		return OrderCreateResponse.from(order);
	}

	public PageResponse<OrderSummaryResponse> getMyOrders(Long memberId, OrderSearchRequest request) {
		OrderSearchCondition condition = request.toCondition(memberId);
		long totalElements = orderQueryMapper.countMyOrders(condition);
		if (totalElements == 0) {
			return PageResponse.of(List.of(), condition.page(), condition.size(), 0);
		}
		List<OrderSummaryResponse> content = orderQueryMapper.findMyOrders(condition);
		return PageResponse.of(content, condition.page(), condition.size(), totalElements);
	}

	public OrderDetailResponse getDetail(Long memberId, Long orderId) {
		Order order = orderRepository.findWithItemsByIdAndMemberId(orderId, memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		Long limitedDropId = limitedPurchaseRepository.findByOrderId(orderId)
				.map(purchase -> purchase.getDrop().getId())
				.orElse(null);
		return OrderDetailResponse.from(order, limitedDropId, resolvePayment(orderId));
	}

	@Transactional
	public OrderDetailResponse cancel(Long memberId, Long orderId, OrderCancelRequest request) {
		// 만료 스케줄러와 같은 주문을 동시에 취소하면 재고가 두 번 복구되므로 주문 행을 먼저 잠근다.
		orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		Order order = orderRepository.findWithItemsByIdAndMemberId(orderId, memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		OrderStatus previousStatus = order.getStatus();
		String reason = request == null ? null : request.reason();
		order.cancel(reason);
		orderStockService.restore(order);
		restoreCoupon(order);
		Optional<LimitedRelease> limitedRelease = limitedPurchaseWriter.revertByOrder(order.getId(),
				LocalDateTime.now(clock));
		limitedRelease.ifPresent(limitedReleaseSynchronizer::releaseAfterCommit);

		if (previousStatus == OrderStatus.PAID) {
			paymentCancelHook.onPaidOrderCanceled(order);
		}
		Long limitedDropId = limitedRelease.map(LimitedRelease::dropId).orElse(null);
		return OrderDetailResponse.from(order, limitedDropId, resolvePayment(orderId));
	}

	/** 승인 이력이 있는 결제(DONE/CANCELED)만 상세 응답에 포함한다. */
	private OrderPaymentResponse resolvePayment(Long orderId) {
		return paymentRepository.findByOrderId(orderId)
				.filter(payment -> payment.getStatus() == PaymentStatus.DONE
						|| payment.getStatus() == PaymentStatus.CANCELED)
				.map(OrderPaymentResponse::from)
				.orElse(null);
	}

	private void restoreCoupon(Order order) {
		if (order.getMemberCoupon() != null && order.getMemberCoupon().isUsed()) {
			order.getMemberCoupon().restore();
		}
	}

	private List<CartItem> findOwnedCartItems(Long memberId, List<Long> cartItemIds) {
		List<Long> distinctIds = cartItemIds.stream().distinct().toList();
		List<CartItem> cartItems = cartItemRepository.findAllByIdInAndCartMemberId(distinctIds, memberId);
		if (cartItems.size() != distinctIds.size()) {
			throw new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND);
		}
		return cartItems;
	}

	private List<OrderLine> resolveCartLines(List<CartItem> cartItems) {
		return cartItems.stream()
				.map(item -> toOrderLine(item.getProduct(), item.getQuantity()))
				.toList();
	}

	private List<OrderLine> resolveDirectLine(Long productId, int quantity) {
		Product product = productRepository.findById(productId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
		return List.of(toOrderLine(product, quantity));
	}

	private OrderLine toOrderLine(Product product, int quantity) {
		if (product.isHidden()) {
			throw new BusinessException(ErrorCode.PRODUCT_HIDDEN);
		}
		if (limitedDropRepository.existsByProductIdAndStatusNot(product.getId(), LimitedDropStatus.CLOSED)) {
			throw new BusinessException(ErrorCode.PRODUCT_LIMITED_ONLY);
		}
		return new OrderLine(product, quantity);
	}

	private Member findActiveMember(Long memberId) {
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		member.validateActive();
		return member;
	}

	private record OrderLine(Product product, int quantity) {
	}
}
