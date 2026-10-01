package com.groove.order.service;

import java.util.Collection;
import java.util.Set;

/**
 * 클레임에 걸린 부분환불(payment_cancel)의 진행 여부를 결제 도메인에서 읽어 온다. 토스에 나간 환불이 아직
 * 확정되지 않았으면(REQUESTED) 철회·거부·재승인이 그 환불과 어긋나므로 이 판정으로 막는다.
 */
public interface OrderClaimRefundReader {

	/** 결과를 기다리는(REQUESTED) 환불이 있는지. */
	boolean hasPendingRefund(Long orderClaimId);

	/** 상태와 무관하게 이 클레임으로 환불을 시도한 적이 있는지. */
	boolean hasAnyRefund(Long orderClaimId);

	/** 클레임 환불이 결과를 기다리는 상품주문 id. */
	Set<Long> findPendingRefundOrderItemIds(Collection<Long> orderItemIds);
}
