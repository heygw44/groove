package com.groove.shipping.domain;

import com.groove.order.domain.Order;
import com.groove.order.domain.OrderShippingInfo;
import com.groove.support.OrderFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Shipping 도메인 — 생성/상태 전이")
class ShippingTest {

    private static final String TRACKING = "8a4f0c2e-1234-4abc-9def-0123456789ab";

    private Order order() {
        return OrderFixtures.memberOrder("ORD-1", 1L);
    }

    @Nested
    @DisplayName("prepare — 배송 준비 시작")
    class Prepare {

        @Test
        @DisplayName("PREPARING 으로 시작, 운송장/수령인/주소 스냅샷 복사")
        void startsPreparing_copiesSnapshot() {
            OrderShippingInfo info = OrderFixtures.sampleShippingInfo();
            Shipping shipping = Shipping.prepare(order(), info, TRACKING);

            assertThat(shipping.getStatus()).isEqualTo(ShippingStatus.PREPARING);
            assertThat(shipping.getTrackingNumber()).isEqualTo(TRACKING);
            assertThat(shipping.getRecipientName()).isEqualTo(info.recipientName());
            assertThat(shipping.getRecipientPhone()).isEqualTo(info.recipientPhone());
            assertThat(shipping.getAddress()).isEqualTo(info.address());
            assertThat(shipping.getAddressDetail()).isEqualTo(info.addressDetail());
            assertThat(shipping.getZipCode()).isEqualTo(info.zipCode());
            assertThat(shipping.isSafePackagingRequested()).isEqualTo(info.safePackagingRequested());
            assertThat(shipping.getShippedAt()).isNull();
            assertThat(shipping.getDeliveredAt()).isNull();
        }

        @Test
        @DisplayName("null order / null shippingInfo → NPE")
        void rejectsNulls() {
            assertThatThrownBy(() -> Shipping.prepare(null, OrderFixtures.sampleShippingInfo(), TRACKING))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> Shipping.prepare(order(), null, TRACKING))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("blank / 50자 초과 운송장 번호 → IllegalArgumentException")
        void rejectsBadTrackingNumber() {
            assertThatThrownBy(() -> Shipping.prepare(order(), OrderFixtures.sampleShippingInfo(), " "))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Shipping.prepare(order(), OrderFixtures.sampleShippingInfo(), "x".repeat(51)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("상태 전이 — markShipped / markDelivered")
    class Transitions {

        private Shipping shipping() {
            return Shipping.prepare(order(), OrderFixtures.sampleShippingInfo(), TRACKING);
        }

        @Test
        @DisplayName("markShipped — PREPARING → SHIPPED, shippedAt 기록")
        void markShipped_recordsShippedAt() {
            Shipping shipping = shipping();

            shipping.markShipped();

            assertThat(shipping.getStatus()).isEqualTo(ShippingStatus.SHIPPED);
            assertThat(shipping.getShippedAt()).isNotNull();
            assertThat(shipping.getDeliveredAt()).isNull();
        }

        @Test
        @DisplayName("markDelivered — SHIPPED → DELIVERED, deliveredAt 기록")
        void markDelivered_recordsDeliveredAt() {
            Shipping shipping = shipping();
            shipping.markShipped();

            shipping.markDelivered();

            assertThat(shipping.getStatus()).isEqualTo(ShippingStatus.DELIVERED);
            assertThat(shipping.getDeliveredAt()).isNotNull();
        }

        @Test
        @DisplayName("markDelivered 를 PREPARING 에서 호출 → IllegalStateException")
        void markDelivered_fromPreparing_throws() {
            Shipping shipping = shipping();

            assertThatThrownBy(shipping::markDelivered)
                    .isInstanceOf(IllegalStateException.class);
            assertThat(shipping.getStatus()).isEqualTo(ShippingStatus.PREPARING);
        }

        @Test
        @DisplayName("markShipped 를 두 번 호출 → 두 번째는 IllegalStateException")
        void markShipped_twice_throws() {
            Shipping shipping = shipping();
            shipping.markShipped();

            assertThatThrownBy(shipping::markShipped)
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("DELIVERED 이후 어떤 전이도 거부")
        void delivered_isTerminal() {
            Shipping shipping = shipping();
            shipping.markShipped();
            shipping.markDelivered();

            assertThatThrownBy(shipping::markShipped).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(shipping::markDelivered).isInstanceOf(IllegalStateException.class);
        }
    }
}
