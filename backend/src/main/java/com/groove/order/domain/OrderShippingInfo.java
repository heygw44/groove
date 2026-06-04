package com.groove.order.domain;

/**
 * 주문 시점에 캡처된 배송지 정보 (ERD §4.9, API.md §3.5 shipping 블록).
 *
 * <p>주문 생성 요청의 {@code shipping} 블록을 도메인으로 들여오는 파라미터 객체이자, 결제 완료 후
 * {@code shipping} 행을 만들 때 그대로 복사되는 스냅샷이다 — {@code album_title_snapshot} 과 같은 이유로
 * 주문 시점 값을 고정한다(회원이 사후에 주소록을 바꿔도 발송된 주문에는 영향이 없다).
 *
 * <p>불변식은 compact 생성자에서 강제한다 — 컬럼 길이는 {@link Order} 의 {@code @Column} 정의와 일치하며,
 * 필수 필드 blank 면 {@link IllegalArgumentException}. API 레이어({@code ShippingInfoRequest} 의 Bean
 * Validation)에서도 같은 제약을 두지만, 도메인 자체로도 한 번 더 막는 이중 방어선이다.
 *
 * @param recipientName           수령인 이름 — blank 불가, {@value Order#MAX_RECIPIENT_NAME_LENGTH}자 이하
 * @param recipientPhone          수령인 연락처 — blank 불가, {@value Order#MAX_RECIPIENT_PHONE_LENGTH}자 이하
 * @param address                 기본 주소 — blank 불가, {@value Order#MAX_ADDRESS_LENGTH}자 이하
 * @param addressDetail           상세 주소 — null 허용, 비어 있으면 null 로 정규화, {@value Order#MAX_ADDRESS_DETAIL_LENGTH}자 이하
 * @param zipCode                 우편번호 — blank 불가, {@value Order#MAX_ZIP_CODE_LENGTH}자 이하
 * @param safePackagingRequested  LP 안전 포장 요청 여부
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
