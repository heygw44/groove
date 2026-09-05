package com.groove.member.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.groove.auth.LoginMember;
import com.groove.auth.resolver.AuthMember;
import com.groove.global.common.ApiResponse;
import com.groove.member.dto.AddressCreateRequest;
import com.groove.member.dto.AddressResponse;
import com.groove.member.dto.AddressUpdateRequest;
import com.groove.member.service.AddressService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Address", description = "배송지")
@RestController
@RequestMapping("/api/v1/members/me/addresses")
@RequiredArgsConstructor
public class AddressController {

	private final AddressService addressService;

	@Operation(summary = "배송지 목록 조회")
	@GetMapping
	public ApiResponse<List<AddressResponse>> getAddresses(@AuthMember LoginMember loginMember) {
		return ApiResponse.ok(addressService.getAddresses(loginMember.id()));
	}

	@Operation(summary = "배송지 등록")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<AddressResponse> create(@AuthMember LoginMember loginMember,
			@Valid @RequestBody AddressCreateRequest request) {
		return ApiResponse.ok(addressService.create(loginMember.id(), request));
	}

	@Operation(summary = "배송지 수정")
	@PatchMapping("/{addressId}")
	public ApiResponse<AddressResponse> update(@AuthMember LoginMember loginMember,
			@PathVariable Long addressId, @Valid @RequestBody AddressUpdateRequest request) {
		return ApiResponse.ok(addressService.update(loginMember.id(), addressId, request));
	}

	@Operation(summary = "배송지 삭제")
	@DeleteMapping("/{addressId}")
	public ApiResponse<Void> delete(@AuthMember LoginMember loginMember, @PathVariable Long addressId) {
		addressService.delete(loginMember.id(), addressId);
		return ApiResponse.ok();
	}

	@Operation(summary = "기본 배송지 지정")
	@PatchMapping("/{addressId}/default")
	public ApiResponse<AddressResponse> setDefault(@AuthMember LoginMember loginMember,
			@PathVariable Long addressId) {
		return ApiResponse.ok(addressService.setDefault(loginMember.id(), addressId));
	}
}
