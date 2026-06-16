package com.groove.claim.application;

import java.util.List;

/**
 * 부분 취소 커맨드 — 관리자 발송 전 부분 취소. 취소 항목 1줄은 ClaimCreateCommand.Line 을 재사용한다.
 */
public record OrderPartialCancelCommand(String orderNumber, String reason, List<ClaimCreateCommand.Line> lines) {

    /** lines 를 방어적 복사로 고정. */
    public OrderPartialCancelCommand {
        lines = (lines == null) ? List.of() : List.copyOf(lines);
    }
}
