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
	COMMON_CONFLICT(HttpStatus.CONFLICT, "요청이 현재 상태와 충돌합니다. 다시 시도해주세요."),
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
	PRODUCT_LIMITED_ONLY(HttpStatus.BAD_REQUEST, "한정반 상품은 한정반 구매로만 주문할 수 있습니다."),
	ARTIST_NOT_FOUND(HttpStatus.NOT_FOUND, "아티스트를 찾을 수 없습니다."),
	LABEL_NOT_FOUND(HttpStatus.NOT_FOUND, "레이블을 찾을 수 없습니다."),
	GENRE_NOT_FOUND(HttpStatus.NOT_FOUND, "장르를 찾을 수 없습니다."),

	// ===== STOCK =====
	STOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "재고 정보를 찾을 수 없습니다."),
	STOCK_INSUFFICIENT(HttpStatus.CONFLICT, "재고가 부족합니다."),
	STOCK_CONFLICT(HttpStatus.CONFLICT, "재고 처리 중 충돌이 발생했습니다. 다시 시도해주세요."),

	// ===== LIMITED =====
	LIMITED_DROP_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 한정반 드롭이 등록된 상품입니다."),
	LIMITED_DROP_NOT_FOUND(HttpStatus.NOT_FOUND, "한정반 드롭을 찾을 수 없습니다."),
	LIMITED_NOT_OPEN(HttpStatus.BAD_REQUEST, "아직 오픈되지 않은 한정반입니다."),
	LIMITED_CLOSED(HttpStatus.BAD_REQUEST, "종료된 한정반입니다."),
	LIMITED_SOLD_OUT(HttpStatus.CONFLICT, "한정반 수량이 모두 소진되었습니다."),
	LIMITED_ALREADY_PURCHASED(HttpStatus.CONFLICT, "이미 구매한 한정반입니다."),
	LIMITED_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "회원당 구매 가능 수량을 초과했습니다."),
	LIMITED_INVALID_STATUS(HttpStatus.CONFLICT, "처리할 수 없는 한정반 상태입니다."),

	// ===== CART =====
	CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "장바구니 항목을 찾을 수 없습니다."),
	CART_EMPTY(HttpStatus.BAD_REQUEST, "장바구니가 비어 있습니다."),
	CART_QUANTITY_EXCEEDED(HttpStatus.BAD_REQUEST, "장바구니 상품은 최대 10개까지 담을 수 있습니다."),

	// ===== ORDER =====
	ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
	ORDER_ALREADY_PAID(HttpStatus.CONFLICT, "이미 결제된 주문입니다."),
	ORDER_CANNOT_CANCEL(HttpStatus.BAD_REQUEST, "취소할 수 없는 주문 상태입니다."),
	ORDER_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "주문 금액이 일치하지 않습니다."),
	ORDER_INVALID_STATUS(HttpStatus.CONFLICT, "처리할 수 없는 주문 상태입니다."),
	ORDER_INVALID_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "허용되지 않는 주문 상태 전이입니다."),

	// ===== PAYMENT =====
	PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제 정보를 찾을 수 없습니다."),
	PAYMENT_ALREADY_DONE(HttpStatus.CONFLICT, "이미 승인된 결제입니다."),
	PAYMENT_INVALID_STATUS(HttpStatus.CONFLICT, "처리할 수 없는 결제 상태입니다."),
	PAYMENT_CONFIRM_FAILED(HttpStatus.BAD_REQUEST, "결제 승인에 실패했습니다."),
	PAYMENT_CANCEL_FAILED(HttpStatus.BAD_REQUEST, "결제 취소에 실패했습니다."),

	// ===== WISHLIST =====
	WISHLIST_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 위시리스트에 등록된 상품입니다."),
	WISHLIST_NOT_FOUND(HttpStatus.NOT_FOUND, "위시리스트에 없는 상품입니다."),

	// ===== COUPON =====
	COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "쿠폰을 찾을 수 없습니다."),
	COUPON_EXPIRED(HttpStatus.BAD_REQUEST, "만료된 쿠폰입니다."),
	COUPON_DISABLED(HttpStatus.BAD_REQUEST, "사용 중지된 쿠폰입니다."),
	COUPON_SOLD_OUT(HttpStatus.CONFLICT, "쿠폰 발급이 모두 소진되었습니다."),
	COUPON_ALREADY_ISSUED(HttpStatus.CONFLICT, "이미 발급받은 쿠폰입니다."),
	COUPON_ALREADY_USED(HttpStatus.CONFLICT, "이미 사용한 쿠폰입니다."),
	COUPON_NOT_USED(HttpStatus.CONFLICT, "사용하지 않은 쿠폰입니다."),
	COUPON_MIN_ORDER_AMOUNT_NOT_MET(HttpStatus.BAD_REQUEST, "최소 주문 금액을 충족하지 않습니다."),
	COUPON_CODE_DUPLICATE(HttpStatus.CONFLICT, "이미 존재하는 쿠폰 코드입니다."),
	COUPON_DISCOUNT_LOCKED(HttpStatus.CONFLICT, "발급이 시작된 쿠폰의 할인 조건은 변경할 수 없습니다."),
	COUPON_QUANTITY_BELOW_ISSUED(HttpStatus.BAD_REQUEST, "총 수량은 발급 수 이상이어야 합니다."),

	// ===== REVIEW =====
	REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "리뷰를 찾을 수 없습니다."),
	REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 작성한 리뷰가 있습니다."),
	REVIEW_PURCHASE_REQUIRED(HttpStatus.FORBIDDEN, "구매한 회원만 리뷰를 작성할 수 있습니다."),

	// ===== FILE =====
	FILE_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 형식입니다."),
	FILE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "파일 크기가 제한을 초과했습니다."),
	FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "파일 업로드에 실패했습니다."),
	FILE_EMPTY(HttpStatus.BAD_REQUEST, "업로드할 파일이 없습니다.");

	private final HttpStatus status;
	private final String message;
}
