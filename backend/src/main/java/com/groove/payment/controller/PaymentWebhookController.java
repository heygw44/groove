package com.groove.payment.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.groove.global.common.ApiResponse;
import com.groove.payment.service.PaymentWebhookService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Payment", description = "결제")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentWebhookController {

	private final PaymentWebhookService paymentWebhookService;

	/**
	 * 발신 검증은 운영 Nginx의 Toss IP 허용 목록과 요청 제한이 맡는다.
	 * 인증도 서명 헤더도 없다. 본문이 JSON 으로 파싱되지 않거나, 대상 이벤트가 아니거나, 우리가 아는
	 * 결제·보상 대기 행이 없는 요청은 로그만 남기고 행을 저장하지 않은 채 200 을 준다 — 무인증
	 * 엔드포인트라 아무나 반복 호출해 DB 를 채울 수 있으므로, 우리가 알아볼 수 있는 이벤트만 남긴다.
	 * DTO(@Valid)로 바로 받으면 파싱 실패가 HttpMessageNotReadableException 으로 전역 예외 핸들러를 거쳐
	 * 400 이 나가버리므로, 원문을 String 으로 받아 서비스가 직접 파싱한다 — 전역 핸들러를 웹훅만 위해
	 * 건드리지 않기 위해서다. 반대로 재조회·적용 자체가 실패하면(이벤트 행은 ERROR 로 남긴 뒤) 예외를 그대로
	 * 던져 200 이 아닌 응답(503)을 준다 — 토스가 최대 7회(3일 19시간) 재전송하게 해서 자동으로 복구되게 한다.
	 */
	@Operation(summary = "토스 웹훅 수신")
	@SecurityRequirements
	@PostMapping("/webhook")
	public ApiResponse<Void> webhook(@RequestBody String rawBody) {
		paymentWebhookService.handle(rawBody);
		return ApiResponse.ok();
	}
}
