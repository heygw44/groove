package com.groove.claim.application;

import java.util.List;

/**
 * 반품 접수 커맨드 (#239) — 회원 식별자 + 대상 주문번호 + 사유 + 반품 항목(OrderItem 단위 부분 반품).
 *
 * @param memberId    인증 회원 식별자 (소유 검증용)
 * @param orderNumber 반품 대상 주문번호
 * @param reason      반품 사유
 * @param lines       반품할 항목들 — 같은 orderItemId 가 여러 번 와도 수량을 합산해 처리한다
 */
public record ClaimCreateCommand(Long memberId, String orderNumber, String reason, List<Line> lines) {

    /**
     * 반품 항목 1줄.
     *
     * @param orderItemId 반품할 주문 항목 식별자
     * @param quantity    반품 수량 (양수 — DTO {@code @Min(1)} 로 선검증)
     */
    public record Line(Long orderItemId, int quantity) {
    }
}
