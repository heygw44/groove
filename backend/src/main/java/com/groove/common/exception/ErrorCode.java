package com.groove.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // AUTH
    AUTH_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH_001", "로그인이 필요합니다. 로그인 후 다시 시도해 주세요"),
    AUTH_FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH_002", "이 작업을 수행할 권한이 없습니다"),
    AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_003", "인증 정보가 올바르지 않습니다. 다시 로그인해 주세요"),
    AUTH_EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_004", "로그인이 만료되었습니다. 다시 로그인해 주세요"),
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다"),

    // VALIDATION
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "VALID_001", "입력하신 정보가 올바르지 않습니다. 다시 확인해 주세요"),

    // DOMAIN
    DOMAIN_RULE_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY, "DOMAIN_003", "요청을 처리할 수 없습니다"),

    // MEMBER
    MEMBER_EMAIL_DUPLICATED(HttpStatus.CONFLICT, "MEMBER_EMAIL_DUPLICATED", "이미 사용 중인 이메일입니다. 다른 이메일을 입력해 주세요"),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "회원을 찾을 수 없습니다"),
    MEMBER_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "MEMBER_PASSWORD_MISMATCH", "현재 비밀번호가 올바르지 않습니다. 다시 확인해 주세요"),
    MEMBER_WITHDRAWAL_BLOCKED(HttpStatus.CONFLICT, "MEMBER_WITHDRAWAL_BLOCKED", "진행 중인 주문이 있어 탈퇴할 수 없습니다. 주문 완료 후 다시 시도해 주세요"),

    // CATALOG
    GENRE_NAME_DUPLICATED(HttpStatus.CONFLICT, "GENRE_NAME_DUPLICATED", "이미 등록된 장르입니다"),
    GENRE_NOT_FOUND(HttpStatus.NOT_FOUND, "GENRE_NOT_FOUND", "장르를 찾을 수 없습니다"),
    GENRE_IN_USE(HttpStatus.CONFLICT, "GENRE_IN_USE", "이 장르를 사용하는 앨범이 있어 삭제할 수 없습니다"),
    LABEL_NAME_DUPLICATED(HttpStatus.CONFLICT, "LABEL_NAME_DUPLICATED", "이미 등록된 레이블입니다"),
    LABEL_NOT_FOUND(HttpStatus.NOT_FOUND, "LABEL_NOT_FOUND", "레이블을 찾을 수 없습니다"),
    LABEL_IN_USE(HttpStatus.CONFLICT, "LABEL_IN_USE", "이 레이블을 사용하는 앨범이 있어 삭제할 수 없습니다"),
    ARTIST_NOT_FOUND(HttpStatus.NOT_FOUND, "ARTIST_NOT_FOUND", "아티스트를 찾을 수 없습니다"),
    ARTIST_IN_USE(HttpStatus.CONFLICT, "ARTIST_IN_USE", "이 아티스트를 사용하는 앨범이 있어 삭제할 수 없습니다"),
    ALBUM_NOT_FOUND(HttpStatus.NOT_FOUND, "ALBUM_NOT_FOUND", "앨범을 찾을 수 없습니다"),
    ALBUM_IN_USE(HttpStatus.CONFLICT, "ALBUM_IN_USE", "이 앨범을 참조하는 장바구니 또는 주문이 있어 삭제할 수 없습니다"),
    ALBUM_INVALID_STOCK(HttpStatus.BAD_REQUEST, "ALBUM_INVALID_STOCK", "재고는 0개 미만으로 설정할 수 없습니다"),
    ALBUM_NOT_PURCHASABLE(HttpStatus.UNPROCESSABLE_ENTITY, "ALBUM_NOT_PURCHASABLE", "현재 구매할 수 없는 앨범입니다"),

    // CART
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND", "장바구니 항목을 찾을 수 없습니다"),
    CART_QUANTITY_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "CART_QUANTITY_LIMIT_EXCEEDED", "담을 수 있는 수량을 초과했습니다"),

    // ORDER
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다"),
    ORDER_INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "ORDER_INVALID_STATE_TRANSITION", "현재 주문 상태에서는 처리할 수 없는 요청입니다"),
    ORDER_INVALID_OWNERSHIP(HttpStatus.UNPROCESSABLE_ENTITY, "ORDER_INVALID_OWNERSHIP", "주문 정보가 올바르지 않습니다"),
    ORDER_ITEM_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "ORDER_ITEM_INVALID", "주문 항목 정보가 올바르지 않습니다"),
    ORDER_INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "ORDER_INSUFFICIENT_STOCK", "재고가 부족합니다"),

    // IDEMPOTENCY
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key 헤더가 필요합니다"),
    IDEMPOTENCY_IN_PROGRESS(HttpStatus.CONFLICT, "IDEMPOTENCY_IN_PROGRESS", "이미 처리 중인 요청입니다. 잠시 후 다시 시도해 주세요"),
    IDEMPOTENCY_KEY_REUSE_MISMATCH(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSE_MISMATCH", "이미 다른 요청에 사용된 Idempotency-Key 입니다"),

    // PAYMENT
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "결제를 찾을 수 없습니다"),
    PAYMENT_NOT_REFUNDABLE(HttpStatus.CONFLICT, "PAYMENT_NOT_REFUNDABLE", "현재 환불할 수 없는 결제입니다"),
    PAYMENT_GATEWAY_FAILURE(HttpStatus.BAD_GATEWAY, "PAYMENT_GATEWAY_FAILURE", "결제 처리에 실패했습니다. 잠시 후 다시 시도해 주세요"),
    PAYMENT_WEBHOOK_INVALID_SIGNATURE(HttpStatus.UNAUTHORIZED, "PAYMENT_WEBHOOK_INVALID_SIGNATURE", "결제 웹훅 서명 검증에 실패했습니다"),

    // SHIPPING
    SHIPPING_NOT_FOUND(HttpStatus.NOT_FOUND, "SHIPPING_NOT_FOUND", "배송 정보를 찾을 수 없습니다"),

    // REVIEW
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "리뷰를 찾을 수 없습니다"),
    REVIEW_NOT_OWNED(HttpStatus.FORBIDDEN, "REVIEW_NOT_OWNED", "본인의 주문 또는 리뷰가 아닙니다"),
    REVIEW_ORDER_NOT_DELIVERED(HttpStatus.UNPROCESSABLE_ENTITY, "REVIEW_ORDER_NOT_DELIVERED", "배송이 완료된 주문에만 리뷰를 작성할 수 있습니다"),
    REVIEW_ALBUM_NOT_IN_ORDER(HttpStatus.UNPROCESSABLE_ENTITY, "REVIEW_ALBUM_NOT_IN_ORDER", "해당 주문에 포함되지 않은 앨범입니다"),
    REVIEW_DUPLICATED(HttpStatus.CONFLICT, "REVIEW_DUPLICATED", "이미 작성한 리뷰입니다"),

    // COUPON (M13 — docs/plans/coupon-system.md §5)
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "COUPON_NOT_FOUND", "쿠폰을 찾을 수 없습니다"),
    COUPON_SOLD_OUT(HttpStatus.CONFLICT, "COUPON_SOLD_OUT", "쿠폰이 모두 소진되었습니다"),
    COUPON_ALREADY_ISSUED(HttpStatus.CONFLICT, "COUPON_ALREADY_ISSUED", "이미 발급받은 쿠폰입니다"),
    COUPON_NOT_ISSUABLE(HttpStatus.UNPROCESSABLE_ENTITY, "COUPON_NOT_ISSUABLE", "현재 발급할 수 없는 쿠폰입니다"),
    COUPON_EXPIRED(HttpStatus.UNPROCESSABLE_ENTITY, "COUPON_EXPIRED", "유효기간이 지난 쿠폰입니다"),
    COUPON_ALREADY_USED(HttpStatus.CONFLICT, "COUPON_ALREADY_USED", "이미 사용한 쿠폰입니다"),
    COUPON_NOT_OWNED(HttpStatus.FORBIDDEN, "COUPON_NOT_OWNED", "본인이 보유한 쿠폰이 아닙니다"),
    COUPON_MIN_ORDER_NOT_MET(HttpStatus.UNPROCESSABLE_ENTITY, "COUPON_MIN_ORDER_NOT_MET", "최소 주문금액을 충족하지 않아 사용할 수 없습니다"),
    COUPON_NOT_APPLICABLE(HttpStatus.UNPROCESSABLE_ENTITY, "COUPON_NOT_APPLICABLE", "주문에 적용할 수 없는 쿠폰입니다"),
    COUPON_INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "COUPON_INVALID_STATE_TRANSITION", "쿠폰 상태를 변경할 수 없습니다"),

    // EXTERNAL
    EXTERNAL_SERVICE_ERROR(HttpStatus.BAD_GATEWAY, "EXT_001", "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요"),

    // SYSTEM
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SYSTEM_001", "서버에 일시적인 문제가 발생했습니다. 잠시 후 다시 시도해 주세요"),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "SYSTEM_002", "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요");

    private final HttpStatus status;
    private final String code;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String code, String defaultMessage) {
        this.status = status;
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
