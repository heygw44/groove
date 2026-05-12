package com.groove.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // AUTH
    AUTH_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH_001", "인증이 필요합니다"),
    AUTH_FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH_002", "접근 권한이 없습니다"),
    AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_003", "유효하지 않은 토큰입니다"),
    AUTH_EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_004", "만료된 토큰입니다"),
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다"),

    // VALIDATION
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "VALID_001", "입력값이 올바르지 않습니다"),

    // DOMAIN
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "DOMAIN_001", "요청한 리소스를 찾을 수 없습니다"),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "DOMAIN_002", "이미 존재하는 리소스입니다"),
    DOMAIN_RULE_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY, "DOMAIN_003", "도메인 규칙 위반입니다"),

    // MEMBER
    MEMBER_EMAIL_DUPLICATED(HttpStatus.CONFLICT, "MEMBER_EMAIL_DUPLICATED", "이미 사용 중인 이메일입니다"),

    // CATALOG
    GENRE_NAME_DUPLICATED(HttpStatus.CONFLICT, "GENRE_NAME_DUPLICATED", "이미 존재하는 장르명입니다"),
    GENRE_NOT_FOUND(HttpStatus.NOT_FOUND, "GENRE_NOT_FOUND", "장르를 찾을 수 없습니다"),
    GENRE_IN_USE(HttpStatus.CONFLICT, "GENRE_IN_USE", "앨범이 참조 중인 장르는 삭제할 수 없습니다"),
    LABEL_NAME_DUPLICATED(HttpStatus.CONFLICT, "LABEL_NAME_DUPLICATED", "이미 존재하는 레이블명입니다"),
    LABEL_NOT_FOUND(HttpStatus.NOT_FOUND, "LABEL_NOT_FOUND", "레이블을 찾을 수 없습니다"),
    LABEL_IN_USE(HttpStatus.CONFLICT, "LABEL_IN_USE", "앨범이 참조 중인 레이블은 삭제할 수 없습니다"),
    ARTIST_NOT_FOUND(HttpStatus.NOT_FOUND, "ARTIST_NOT_FOUND", "아티스트를 찾을 수 없습니다"),
    ARTIST_IN_USE(HttpStatus.CONFLICT, "ARTIST_IN_USE", "앨범이 참조 중인 아티스트는 삭제할 수 없습니다"),
    ALBUM_NOT_FOUND(HttpStatus.NOT_FOUND, "ALBUM_NOT_FOUND", "앨범을 찾을 수 없습니다"),
    ALBUM_INVALID_STOCK(HttpStatus.BAD_REQUEST, "ALBUM_INVALID_STOCK", "재고 조정 결과가 음수가 될 수 없습니다"),
    ALBUM_NOT_PURCHASABLE(HttpStatus.UNPROCESSABLE_ENTITY, "ALBUM_NOT_PURCHASABLE", "현재 구매할 수 없는 앨범입니다"),

    // CART
    CART_NOT_FOUND(HttpStatus.NOT_FOUND, "CART_NOT_FOUND", "장바구니를 찾을 수 없습니다"),
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND", "장바구니 항목을 찾을 수 없습니다"),
    CART_QUANTITY_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "CART_QUANTITY_LIMIT_EXCEEDED", "허용된 수량 한도를 초과했습니다"),

    // ORDER
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다"),
    ORDER_INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "ORDER_INVALID_STATE_TRANSITION", "허용되지 않은 주문 상태 전이입니다"),
    ORDER_INVALID_OWNERSHIP(HttpStatus.UNPROCESSABLE_ENTITY, "ORDER_INVALID_OWNERSHIP", "주문 소유 형식이 올바르지 않습니다"),
    ORDER_ITEM_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "ORDER_ITEM_INVALID", "주문 항목 값이 올바르지 않습니다"),
    ORDER_INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "ORDER_INSUFFICIENT_STOCK", "재고가 부족합니다"),

    // IDEMPOTENCY
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key 헤더가 필요합니다"),
    IDEMPOTENCY_IN_PROGRESS(HttpStatus.CONFLICT, "IDEMPOTENCY_IN_PROGRESS", "동일한 Idempotency-Key 요청이 처리 중입니다"),
    IDEMPOTENCY_KEY_REUSE_MISMATCH(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSE_MISMATCH", "이미 다른 요청에 사용된 Idempotency-Key 입니다"),

    // PAYMENT
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "결제를 찾을 수 없습니다"),
    PAYMENT_GATEWAY_FAILURE(HttpStatus.BAD_GATEWAY, "PAYMENT_GATEWAY_FAILURE", "결제 게이트웨이 연동에 실패했습니다"),

    // EXTERNAL
    EXTERNAL_SERVICE_ERROR(HttpStatus.BAD_GATEWAY, "EXT_001", "외부 서비스 연동에 실패했습니다"),

    // SYSTEM
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SYSTEM_001", "서버 내부 오류가 발생했습니다"),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "SYSTEM_002", "요청이 너무 많습니다");

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
