package com.groove.claim.domain;

/**
 * 클레임 종류 — 취소(발송 전)와 반품(발송 후)을 하나의 Claim aggregate 로 통합한다.
 * <ul>
 *   <li>CANCEL : 발송 전(주문 PAID/PREPARING) 부분 취소. REQUESTED → REFUNDED 1-스텝 즉시 환불.</li>
 *   <li>RETURN : 발송 후(주문 DELIVERED/COMPLETED) 반품. REQUESTED → APPROVED → IN_TRANSIT → INSPECTING →
 *       REFUNDED/REJECTED 역물류 상태머신.</li>
 * </ul>
 */
public enum ClaimType {

    /** 발송 전 부분 취소 — 즉시 환불. */
    CANCEL,

    /** 발송 후 반품 — 역물류 상태머신. */
    RETURN
}
