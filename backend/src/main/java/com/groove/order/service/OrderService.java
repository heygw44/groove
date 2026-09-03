package com.groove.order.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.cart.entity.CartItem;
import com.groove.cart.repository.CartItemRepository;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.inventory.entity.Stock;
import com.groove.inventory.entity.StockChangeType;
import com.groove.inventory.entity.StockHistory;
import com.groove.inventory.repository.StockHistoryRepository;
import com.groove.inventory.repository.StockRepository;
import com.groove.member.entity.Address;
import com.groove.member.entity.Member;
import com.groove.member.repository.AddressRepository;
import com.groove.member.repository.MemberRepository;
import com.groove.order.dto.OrderCreateRequest;
import com.groove.order.dto.OrderCreateResponse;
import com.groove.order.entity.Order;
import com.groove.order.entity.ShippingAddress;
import com.groove.order.repository.OrderRepository;
import com.groove.product.entity.Product;
import com.groove.product.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

/** 주문 생성. 장바구니 항목 또는 단일 상품 중 하나를 원천으로 재고를 차감하고 PENDING 주문을 만든다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrderService {

	private static final String STOCK_OUT_REASON_PREFIX = "주문 ";

	private final MemberRepository memberRepository;
	private final AddressRepository addressRepository;
	private final ProductRepository productRepository;
	private final CartItemRepository cartItemRepository;
	private final StockRepository stockRepository;
	private final StockHistoryRepository stockHistoryRepository;
	private final OrderRepository orderRepository;
	private final OrderNumberGenerator orderNumberGenerator;

	@Transactional
	public OrderCreateResponse create(Long memberId, OrderCreateRequest request) {
		Member member = findActiveMember(memberId);
		Address address = addressRepository.findByIdAndMemberId(request.addressId(), memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_ADDRESS_NOT_FOUND));

		List<CartItem> cartItems = request.isFromCart()
				? findOwnedCartItems(memberId, request.cartItemIds())
				: List.of();
		List<OrderLine> lines = request.isFromCart()
				? resolveCartLines(cartItems)
				: resolveDirectLine(request.productId(), request.quantity());

		Map<Long, Stock> stocksByProductId = lockStocks(lines);
		String orderNumber = orderNumberGenerator.generate();
		decreaseStocksAndRecordHistory(lines, stocksByProductId, orderNumber);

		Order order = Order.create(orderNumber, member, ShippingAddress.from(address));
		for (OrderLine line : lines) {
			order.addItem(line.product(), line.quantity());
		}
		orderRepository.save(order);

		if (request.isFromCart()) {
			cartItemRepository.deleteAll(cartItems);
		}
		return OrderCreateResponse.from(order);
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
		return new OrderLine(product, quantity);
	}

	private Map<Long, Stock> lockStocks(List<OrderLine> lines) {
		List<Long> productIds = lines.stream()
				.map(line -> line.product().getId())
				.distinct()
				.toList();
		List<Stock> stocks = stockRepository.findAllWithProductByProductIdInForUpdate(productIds);
		if (stocks.size() != productIds.size()) {
			throw new BusinessException(ErrorCode.STOCK_NOT_FOUND);
		}
		return stocks.stream()
				.collect(Collectors.toMap(stock -> stock.getProduct().getId(), stock -> stock));
	}

	private void decreaseStocksAndRecordHistory(List<OrderLine> lines, Map<Long, Stock> stocksByProductId,
			String orderNumber) {
		List<OrderLine> sortedLines = lines.stream()
				.sorted(Comparator.comparing(line -> line.product().getId()))
				.toList();
		for (OrderLine line : sortedLines) {
			Stock stock = stocksByProductId.get(line.product().getId());
			stock.decrease(line.quantity());
		}

		// 이력 INSERT 가 stock 행에 FK 공유 락을 잡아 UPDATE 와 데드락이 나므로 재고 UPDATE 를 먼저 flush 한다.
		stockRepository.flush();

		for (OrderLine line : sortedLines) {
			Stock stock = stocksByProductId.get(line.product().getId());
			stockHistoryRepository.save(StockHistory.of(stock, StockChangeType.OUT, -line.quantity(),
					STOCK_OUT_REASON_PREFIX + orderNumber));
		}
	}

	private Member findActiveMember(Long memberId) {
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		if (member.isWithdrawn()) {
			throw new BusinessException(ErrorCode.MEMBER_WITHDRAWN);
		}
		return member;
	}

	private record OrderLine(Product product, int quantity) {
	}
}
