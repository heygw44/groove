package com.groove.claim.domain;

/**
 * 클레임 종류 (#238) — 취소(발송 전)와 반품(발송 후)을 하나의 {@link Claim} aggregate 로 통합한다.
 *
 * <p>네이버 커머스의 통합 클레임 모델과 동일한 접근 — 취소·반품을 별도 메커니즘으로 분리하지 않고 타입으로 구분한다.
 * 두 타입은 자격 윈도우와 상태 경로가 다르다:
 * <ul>
 *   <li>{@link #CANCEL} : 발송 <b>전</b>(주문 {@code PAID}/{@code PREPARING}) 부분 취소. 회수·검수가 없어
 *       {@code REQUESTED → REFUNDED} 1-스텝으로 즉시 환불한다 — 관리자 트리거 (#238).</li>
 *   <li>{@link #RETURN} : 발송 <b>후</b>(주문 {@code DELIVERED}/{@code COMPLETED}) 반품. 역물류 상태머신
 *       {@code REQUESTED → APPROVED → IN_TRANSIT → INSPECTING → REFUNDED}/{@code REJECTED} 를 거친다 (#239).</li>
 * </ul>
 *
 * <p>{@code RETURN} 은 #239 의 기존 동작이며, V27 이전에 생성된 모든 claim 행은 RETURN 으로 백필된다.
 */
public enum ClaimType {

    /** 발송 전 부분 취소 — 즉시 환불 (#238). */
    CANCEL,

    /** 발송 후 반품 — 역물류 상태머신 (#239). */
    RETURN
}
