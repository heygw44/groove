package com.groove.payment.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.groove.auth.LoginMember;
import com.groove.auth.resolver.AuthMember;
import com.groove.global.common.ApiResponse;
import com.groove.payment.dto.PaymentConfirmRequest;
import com.groove.payment.dto.PaymentConfirmResponse;
import com.groove.payment.service.PaymentConfirmService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Payment", description = "결제")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

	private final PaymentConfirmService paymentConfirmService;

	@Operation(summary = "결제 승인")
	@PostMapping("/confirm")
	public ApiResponse<PaymentConfirmResponse> confirm(@AuthMember LoginMember loginMember,
			@Valid @RequestBody PaymentConfirmRequest request) {
		return ApiResponse.ok(paymentConfirmService.confirm(loginMember.id(), request));
	}

}
