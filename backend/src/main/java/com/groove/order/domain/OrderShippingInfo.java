package com.groove.order.domain;

/**
 * 주문 시점에 캡처된 배송지 정보 스냅샷. 불변식은 compact 생성자에서 강제한다 —
 * 필수 필드 blank 또는 길이 초과면 IllegalArgumentException, addressDetail 은 null 허용(빈 값은 null 정규화).
 */
public record OrderShippingInfo(
        String recipientName,
        String recipientPhone,
        String address,
        String addressDetail,
        String zipCode,
        boolean safePackagingRequested) {

    public OrderShippingInfo {
        recipientName = requireWithin("recipientName", recipientName, Order.MAX_RECIPIENT_NAME_LENGTH);
        recipientPhone = requireWithin("recipientPhone", recipientPhone, Order.MAX_RECIPIENT_PHONE_LENGTH);
        address = requireWithin("address", address, Order.MAX_ADDRESS_LENGTH);
        addressDetail = optionalWithin("addressDetail", addressDetail, Order.MAX_ADDRESS_DETAIL_LENGTH);
        zipCode = requireWithin("zipCode", zipCode, Order.MAX_ZIP_CODE_LENGTH);
    }

    private static String requireWithin(String field, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String trimmed = value.strip();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(field + " length must be <= " + maxLength);
        }
        return trimmed;
    }

    private static String optionalWithin(String field, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(field + " length must be <= " + maxLength);
        }
        return trimmed;
    }
}
