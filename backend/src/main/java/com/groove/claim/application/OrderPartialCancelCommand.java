package com.groove.claim.application;

import java.util.List;

/**
 * 부분 취소 커맨드 (#238) — 관리자 발송 전 부분 취소. 관리자 권한 경로라 회원 소유 검증이 없어 memberId 를 두지 않는다
 * ({@code ClaimCreateCommand} 와의 차이). 취소 항목 1줄은 {@link ClaimCreateCommand.Line} 을 재사용한다.
 *
 * @param orderNumber 취소 대상 주문번호
 * @param reason      취소 사유 (필수)
 * @param lines       취소할 항목들 — 같은 orderItemId 가 여러 번 와도 수량을 합산해 처리한다
 */
public record OrderPartialCancelCommand(String orderNumber, String reason, List<ClaimCreateCommand.Line> lines) {
}
