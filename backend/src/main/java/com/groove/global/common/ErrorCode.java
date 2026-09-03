package com.groove.global.common;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 에러 코드 규약: {도메인}_{원인} 대문자 스네이크.
 * 도메인 기능이 추가될 때마다 해당 도메인 섹션에 코드를 추가한다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	// COMMON
	COMMON_INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
	COMMON_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값 검증에 실패했습니다."),
	COMMON_METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "허용되지 않은 HTTP 메서드입니다."),
	COMMON_RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
	COMMON_INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

	// AUTH
	AUTH_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
	AUTH_FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
	AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "인증 정보가 올바르지 않습니다."),
	AUTH_EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다."),
	AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
	AUTH_REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다. 다시 로그인해주세요."),
	AUTH_REFRESH_TOKEN_MISMATCH(HttpStatus.UNAUTHORIZED, "다른 기기에서 로그인되어 로그아웃되었습니다. 다시 로그인해주세요."),

	// ===== MEMBER =====
	MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
	MEMBER_EMAIL_DUPLICATE(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
	MEMBER_WITHDRAWN(HttpStatus.FORBIDDEN, "탈퇴한 회원입니다."),
	MEMBER_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다."),
	MEMBER_ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "배송지를 찾을 수 없습니다."),
	MEMBER_ADDRESS_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "등록 가능한 배송지 수를 초과했습니다."),

	// ===== PRODUCT =====
	PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
	PRODUCT_HIDDEN(HttpStatus.NOT_FOUND, "판매 중지된 상품입니다."),
	PRODUCT_NOT_HIDDEN(HttpStatus.CONFLICT, "숨김 상태가 아닌 상품입니다."),
	ARTIST_NOT_FOUND(HttpStatus.NOT_FOUND, "아티스트를 찾을 수 없습니다."),
	LABEL_NOT_FOUND(HttpStatus.NOT_FOUND, "레이블을 찾을 수 없습니다."),
	GENRE_NOT_FOUND(HttpStatus.NOT_FOUND, "장르를 찾을 수 없습니다."),

	// ===== STOCK =====
	STOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "재고 정보를 찾을 수 없습니다."),
	STOCK_INSUFFICIENT(HttpStatus.CONFLICT, "재고가 부족합니다."),
	STOCK_CONFLICT(HttpStatus.CONFLICT, "재고 처리 중 충돌이 발생했습니다. 다시 시도해주세요."),

	// ===== FILE =====
	FILE_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 형식입니다."),
	FILE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "파일 크기가 제한을 초과했습니다."),
	FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "파일 업로드에 실패했습니다."),
	FILE_EMPTY(HttpStatus.BAD_REQUEST, "업로드할 파일이 없습니다.");

	private final HttpStatus status;
	private final String message;
}
