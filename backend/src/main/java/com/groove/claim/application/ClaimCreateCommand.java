package com.groove.claim.application;

import java.util.List;

/** 반품 접수 커맨드 — 회원 식별자 + 대상 주문번호 + 사유 + 반품 항목. */
public record ClaimCreateCommand(Long memberId, String orderNumber, String reason, List<Line> lines) {

    /** 반품 항목 1줄. */
    public record Line(Long orderItemId, int quantity) {
    }
}
