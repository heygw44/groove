package com.groove.order.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.order.dto.OrderCancelRequest;
import com.groove.order.dto.OrderItemResponse;
import com.groove.order.dto.OrderReturnRequest;
import com.groove.order.entity.OrderItem;
import com.groove.order.repository.OrderItemRepository;
import com.groove.payment.client.dto.RefundAccountInfo;
import com.groove.product.repository.ProductImageRepository;

import lombok.RequiredArgsConstructor;

/**
 * 구매자의 상품 단위 취소·반품·철회. 결제 있는 취소는 요청 기록(T1, {@link OrderClaimWriter}) → 트랜잭션 밖
 * 환불 시도({@link OrderClaimRefundHook}) 순으로 처리한다 - 이 클래스 자체는 트랜잭션을 열지 않는다(주문 행
 * 잠금이 토스 호출 동안 유지되면 안 된다).
 */
@Service
@RequiredArgsConstructor
public class OrderItemClaimService {

	private final OrderClaimWriter writer;
	private final OrderClaimRefundHook refundHook;
	private final OrderItemRepository orderItemRepository;
	private final ProductImageRepository productImageRepository;
	private final Clock clock;

	/** {@code PAID} 면 즉시 취소·부분환불, {@code PREPARING} 이면 클레임 요청만 남기고 관리자 승인을 기다린다. */
	public OrderItemResponse cancel(Long memberId, Long orderId, Long itemId, OrderCancelRequest request) {
		String reason = request == null ? null : request.reason();
		RefundAccountInfo refundAccount = toRefundAccount(request);
		OrderClaimRequestResult result = writer.requestCancel(memberId, orderId, itemId, reason, refundAccount);
		if (result.immediate()) {
			refundHook.refund(orderId, result.claimId(), result.refundAmount(), reason, refundAccount);
		}
		return buildResponse(itemId);
	}

	/** 배송완료 후 7일 이내(D7)인 상품주문에 반품 클레임을 만든다. 환불은 관리자 수거 완료 시점에 일어난다. */
	public OrderItemResponse returnItem(Long memberId, Long orderId, Long itemId, OrderReturnRequest request) {
		String reason = request == null ? null : request.reason();
		writer.requestReturn(memberId, orderId, itemId, reason);
		return buildResponse(itemId);
	}

	/** {@code REQUESTED} 상태인 본인 클레임을 철회한다. */
	public OrderItemResponse withdraw(Long memberId, Long claimId) {
		Long itemId = writer.withdraw(memberId, claimId);
		return buildResponse(itemId);
	}

	private OrderItemResponse buildResponse(Long itemId) {
		OrderItem item = orderItemRepository.findWithProductById(itemId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		String thumbnailUrl = productImageRepository
				.findAllByProductIdInAndSortOrder(List.of(item.getProduct().getId()), 0)
				.stream()
				.findFirst()
				.map(image -> image.getImageUrl())
				.orElse(null);
		return OrderItemResponse.from(item, thumbnailUrl, LocalDateTime.now(clock));
	}

	private RefundAccountInfo toRefundAccount(OrderCancelRequest request) {
		if (request == null || request.refundAccount() == null) {
			return null;
		}
		OrderCancelRequest.RefundAccount refundAccount = request.refundAccount();
		return new RefundAccountInfo(refundAccount.bankCode(), refundAccount.accountNumber(),
				refundAccount.holderName());
	}
}
