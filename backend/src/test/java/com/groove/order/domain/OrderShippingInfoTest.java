package com.groove.order.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OrderShippingInfo compact 생성자 불변식 전수 검증 (#142 갭 보강).
 *
 * <p>그동안 {@code OrderShippingInfo} 는 Order 생성의 정상 경로로만 간접 실행돼,
 * blank·길이초과 거부 / {@code strip()} 정규화 / 선택 필드(addressDetail) null 정규화 분기가
 * 직접 검증되지 않았다. API 레이어(ShippingInfoRequest Bean Validation)와 별개의
 * 도메인 이중 방어선이므로 도메인 단독으로 한 번 더 확인한다.
 */
@DisplayName("OrderShippingInfo — 배송지 스냅샷 불변식")
class OrderShippingInfoTest {

    private static OrderShippingInfo valid() {
        return new OrderShippingInfo("김철수", "01012345678", "서울시 강남구 테헤란로 123", "456호", "06234", true);
    }

    @Nested
    @DisplayName("정상 생성 · 정규화")
    class Valid {

        @Test
        @DisplayName("유효값 — 모든 필드 보존")
        void preservesFields() {
            OrderShippingInfo info = valid();

            assertThat(info.recipientName()).isEqualTo("김철수");
            assertThat(info.recipientPhone()).isEqualTo("01012345678");
            assertThat(info.address()).isEqualTo("서울시 강남구 테헤란로 123");
            assertThat(info.addressDetail()).isEqualTo("456호");
            assertThat(info.zipCode()).isEqualTo("06234");
            assertThat(info.safePackagingRequested()).isTrue();
        }

        @Test
        @DisplayName("앞뒤 공백은 strip() 으로 정규화 (내부 공백은 보존)")
        void stripsSurroundingWhitespace() {
            OrderShippingInfo info = new OrderShippingInfo(
                    "  김철수  ", "  01012345678  ", "  서울시 강남구  ", "  456호  ", "  06234  ", false);

            assertThat(info.recipientName()).isEqualTo("김철수");
            assertThat(info.recipientPhone()).isEqualTo("01012345678");
            assertThat(info.address()).isEqualTo("서울시 강남구");
            assertThat(info.addressDetail()).isEqualTo("456호");
            assertThat(info.zipCode()).isEqualTo("06234");
        }

        @Test
        @DisplayName("경계 길이(=MAX) 는 허용")
        void allowsExactlyMaxLength() {
            OrderShippingInfo info = new OrderShippingInfo(
                    "가".repeat(Order.MAX_RECIPIENT_NAME_LENGTH),
                    "01012345678",
                    "주".repeat(Order.MAX_ADDRESS_LENGTH),
                    "상".repeat(Order.MAX_ADDRESS_DETAIL_LENGTH),
                    "06234",
                    false);

            assertThat(info.recipientName()).hasSize(Order.MAX_RECIPIENT_NAME_LENGTH);
            assertThat(info.address()).hasSize(Order.MAX_ADDRESS_LENGTH);
            assertThat(info.addressDetail()).hasSize(Order.MAX_ADDRESS_DETAIL_LENGTH);
        }
    }

    @Nested
    @DisplayName("필수 필드 검증")
    class RequiredFields {

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.groove.order.domain.OrderShippingInfoTest#blankCases")
        @DisplayName("null/blank → IllegalArgumentException(must not be blank)")
        void rejectsBlank(String field, Runnable construct) {
            assertThatThrownBy(construct::run)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(field)
                    .hasMessageContaining("must not be blank");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.groove.order.domain.OrderShippingInfoTest#overLengthCases")
        @DisplayName("MAX 초과 → IllegalArgumentException(length must be <=)")
        void rejectsOverLength(String field, Runnable construct) {
            assertThatThrownBy(construct::run)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(field)
                    .hasMessageContaining("length must be <=");
        }
    }

    @Nested
    @DisplayName("addressDetail — 선택 필드 (null 허용)")
    class AddressDetail {

        @Test
        @DisplayName("null → null 로 정규화")
        void nullStaysNull() {
            OrderShippingInfo info = new OrderShippingInfo(
                    "김철수", "01012345678", "서울시 강남구", null, "06234", false);

            assertThat(info.addressDetail()).isNull();
        }

        @Test
        @DisplayName("blank → null 로 정규화")
        void blankBecomesNull() {
            OrderShippingInfo info = new OrderShippingInfo(
                    "김철수", "01012345678", "서울시 강남구", "   ", "06234", false);

            assertThat(info.addressDetail()).isNull();
        }

        @Test
        @DisplayName("MAX 초과 → IllegalArgumentException")
        void rejectsOverLength() {
            assertThatThrownBy(() -> new OrderShippingInfo(
                    "김철수", "01012345678", "서울시 강남구",
                    "상".repeat(Order.MAX_ADDRESS_DETAIL_LENGTH + 1), "06234", false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("addressDetail")
                    .hasMessageContaining("length must be <=");
        }
    }

    static Stream<Arguments> blankCases() {
        return Stream.of(
                Arguments.of("recipientName", (Runnable) () ->
                        new OrderShippingInfo(null, "01012345678", "서울", "456호", "06234", false)),
                Arguments.of("recipientName", (Runnable) () ->
                        new OrderShippingInfo(" ", "01012345678", "서울", "456호", "06234", false)),
                Arguments.of("recipientPhone", (Runnable) () ->
                        new OrderShippingInfo("김철수", null, "서울", "456호", "06234", false)),
                Arguments.of("recipientPhone", (Runnable) () ->
                        new OrderShippingInfo("김철수", " ", "서울", "456호", "06234", false)),
                Arguments.of("address", (Runnable) () ->
                        new OrderShippingInfo("김철수", "01012345678", null, "456호", "06234", false)),
                Arguments.of("address", (Runnable) () ->
                        new OrderShippingInfo("김철수", "01012345678", " ", "456호", "06234", false)),
                Arguments.of("zipCode", (Runnable) () ->
                        new OrderShippingInfo("김철수", "01012345678", "서울", "456호", null, false)),
                Arguments.of("zipCode", (Runnable) () ->
                        new OrderShippingInfo("김철수", "01012345678", "서울", "456호", " ", false))
        );
    }

    static Stream<Arguments> overLengthCases() {
        return Stream.of(
                Arguments.of("recipientName", (Runnable) () -> new OrderShippingInfo(
                        "가".repeat(Order.MAX_RECIPIENT_NAME_LENGTH + 1),
                        "01012345678", "서울", "456호", "06234", false)),
                Arguments.of("recipientPhone", (Runnable) () -> new OrderShippingInfo(
                        "김철수", "1".repeat(Order.MAX_RECIPIENT_PHONE_LENGTH + 1),
                        "서울", "456호", "06234", false)),
                Arguments.of("address", (Runnable) () -> new OrderShippingInfo(
                        "김철수", "01012345678",
                        "주".repeat(Order.MAX_ADDRESS_LENGTH + 1), "456호", "06234", false)),
                Arguments.of("zipCode", (Runnable) () -> new OrderShippingInfo(
                        "김철수", "01012345678", "서울", "456호",
                        "1".repeat(Order.MAX_ZIP_CODE_LENGTH + 1), false))
        );
    }
}
