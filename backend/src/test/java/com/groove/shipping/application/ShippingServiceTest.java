package com.groove.shipping.application;

import com.groove.order.domain.Order;
import com.groove.order.domain.OrderStatus;
import com.groove.shipping.api.dto.ShippingResponse;
import com.groove.shipping.domain.Shipping;
import com.groove.shipping.domain.ShippingRepository;
import com.groove.shipping.domain.ShippingStatus;
import com.groove.shipping.exception.ShippingNotFoundException;
import com.groove.support.OrderFixtures;
import com.groove.support.TestClocks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShippingService 단위 테스트")
class ShippingServiceTest {

    private static final String TRACKING = "8a4f0c2e-1234-4abc-9def-0123456789ab";

    private static final Clock CLOCK = TestClocks.FIXED;

    @Mock
    private ShippingRepository shippingRepository;

    private ShippingService shippingService;

    @BeforeEach
    void setUp() {
        shippingService = new ShippingService(shippingRepository, CLOCK);
    }

    private Shipping preparingShipping() {
        return shippingWithOrderAt(OrderStatus.PENDING);
    }

    /** 연관 주문을 지정한 상태까지 전진시킨 PREPARING 배송을 만든다. */
    private Shipping shippingWithOrderAt(OrderStatus orderStatus) {
        Order order = OrderFixtures.memberOrder("ORD-1", 1L);
        pathTo(orderStatus).forEach(s -> order.changeStatus(s, null, CLOCK.instant()));
        return Shipping.prepare(order, OrderFixtures.sampleShippingInfo(), TRACKING);
    }

    private static List<OrderStatus> pathTo(OrderStatus target) {
        return switch (target) {
            case PENDING -> List.of();
            case PAID -> List.of(OrderStatus.PAID);
            case PREPARING -> List.of(OrderStatus.PAID, OrderStatus.PREPARING);
            case SHIPPED -> List.of(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.SHIPPED);
            case DELIVERED -> List.of(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.SHIPPED, OrderStatus.DELIVERED);
            case CANCELLED -> List.of(OrderStatus.CANCELLED);
            default -> throw new IllegalArgumentException("지원하지 않는 목표 상태: " + target);
        };
    }

    @Test
    @DisplayName("findByTrackingNumber — 존재하면 ShippingResponse 반환")
    void findByTrackingNumber_found() {
        given(shippingRepository.findByTrackingNumber(TRACKING)).willReturn(Optional.of(preparingShipping()));

        ShippingResponse response = shippingService.findByTrackingNumber(TRACKING);

        assertThat(response.trackingNumber()).isEqualTo(TRACKING);
        assertThat(response.status()).isEqualTo(ShippingStatus.PREPARING);
        assertThat(response.recipientName()).isEqualTo("김철수");
    }

    @Test
    @DisplayName("findByTrackingNumber — 미존재 → ShippingNotFoundException")
    void findByTrackingNumber_notFound() {
        given(shippingRepository.findByTrackingNumber("nope")).willReturn(Optional.empty());

        assertThatThrownBy(() -> shippingService.findByTrackingNumber("nope"))
                .isInstanceOf(ShippingNotFoundException.class);
    }

    @Test
    @DisplayName("advanceToShipped — PREPARING 이면 SHIPPED 로 전이하고 주문도 SHIPPED 로 동반 전이")
    void advanceToShipped_fromPreparing() {
        Shipping shipping = shippingWithOrderAt(OrderStatus.PREPARING);
        given(shippingRepository.findWithOrderById(1L)).willReturn(Optional.of(shipping));

        shippingService.advanceToShipped(1L);

        assertThat(shipping.getStatus()).isEqualTo(ShippingStatus.SHIPPED);
        assertThat(shipping.getOrder().getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    @DisplayName("advanceToShipped — 이미 SHIPPED 면 무시 (멱등) — 주문 전이는 배송 가드 안에서만 일어나 주문도 불변")
    void advanceToShipped_alreadyShipped_noop() {
        // 배송 SHIPPED, 주문 PREPARING — 배송 가드에서 단락돼 주문이 PREPARING 으로 남는다.
        Shipping shipping = shippingWithOrderAt(OrderStatus.PREPARING);
        shipping.markShipped(CLOCK.instant());
        given(shippingRepository.findWithOrderById(1L)).willReturn(Optional.of(shipping));

        shippingService.advanceToShipped(1L);

        assertThat(shipping.getStatus()).isEqualTo(ShippingStatus.SHIPPED);
        assertThat(shipping.getOrder().getStatus()).isEqualTo(OrderStatus.PREPARING);
    }

    @Test
    @DisplayName("advanceToShipped — 대상 없으면 무해")
    void advanceToShipped_missing_noop() {
        given(shippingRepository.findWithOrderById(99L)).willReturn(Optional.empty());

        shippingService.advanceToShipped(99L);
    }

    @Test
    @DisplayName("advanceToDelivered — SHIPPED 면 DELIVERED 로 전이하고 주문도 DELIVERED 로 동반 전이")
    void advanceToDelivered_fromShipped() {
        Shipping shipping = shippingWithOrderAt(OrderStatus.SHIPPED);
        shipping.markShipped(CLOCK.instant());
        given(shippingRepository.findWithOrderById(1L)).willReturn(Optional.of(shipping));

        shippingService.advanceToDelivered(1L);

        assertThat(shipping.getStatus()).isEqualTo(ShippingStatus.DELIVERED);
        assertThat(shipping.getOrder().getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    @DisplayName("advanceToDelivered — 배송이 PREPARING 이면 무시 (멱등) — 주문도 배송 가드 안에서만 전이돼 불변")
    void advanceToDelivered_fromPreparing_noop() {
        // 배송 PREPARING, 주문 SHIPPED — 배송 가드에서 단락돼 주문이 SHIPPED 로 남는다.
        Shipping shipping = shippingWithOrderAt(OrderStatus.SHIPPED);
        given(shippingRepository.findWithOrderById(1L)).willReturn(Optional.of(shipping));

        shippingService.advanceToDelivered(1L);

        assertThat(shipping.getStatus()).isEqualTo(ShippingStatus.PREPARING);
        assertThat(shipping.getOrder().getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    @DisplayName("advanceToDelivered — 주문이 한 단계 뒤처져(PREPARING) 있으면 불법 점프 대신 무해 무시 (가드가 예외 차단)")
    void advanceToDelivered_orderBehind_guardSkipsWithoutThrowing() {
        // 배송 SHIPPED, 주문 PREPARING — PREPARING→DELIVERED 는 불법이라 가드가 예외를 막는다.
        Shipping shipping = shippingWithOrderAt(OrderStatus.PREPARING);
        shipping.markShipped(CLOCK.instant());
        given(shippingRepository.findWithOrderById(1L)).willReturn(Optional.of(shipping));

        assertThatCode(() -> shippingService.advanceToDelivered(1L)).doesNotThrowAnyException();

        assertThat(shipping.getStatus()).isEqualTo(ShippingStatus.DELIVERED);
        assertThat(shipping.getOrder().getStatus()).isEqualTo(OrderStatus.PREPARING);
    }

    @Test
    @DisplayName("advanceToShipped — 주문이 취소(종착)면 배송을 전진시키지 않는다 (#233) — 배송 PREPARING 유지")
    void advanceToShipped_orderTerminal_skipsAdvance() {
        // 주문 CANCELLED, 배송 PREPARING — 스케줄러가 밀면 안 된다.
        Shipping shipping = shippingWithOrderAt(OrderStatus.CANCELLED);
        given(shippingRepository.findWithOrderById(1L)).willReturn(Optional.of(shipping));

        shippingService.advanceToShipped(1L);

        assertThat(shipping.getStatus()).isEqualTo(ShippingStatus.PREPARING);
        assertThat(shipping.getOrder().getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("advanceToDelivered — 주문이 취소(종착)면 배송을 전진시키지 않는다 (#233) — 배송 SHIPPED 유지, 예외 없음")
    void advanceToDelivered_orderTerminal_skipsAdvance() {
        Shipping shipping = shippingWithOrderAt(OrderStatus.CANCELLED);
        shipping.markShipped(CLOCK.instant());
        given(shippingRepository.findWithOrderById(1L)).willReturn(Optional.of(shipping));

        assertThatCode(() -> shippingService.advanceToDelivered(1L)).doesNotThrowAnyException();

        assertThat(shipping.getStatus()).isEqualTo(ShippingStatus.SHIPPED);
        assertThat(shipping.getOrder().getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Nested
    @DisplayName("cancelForOrder — 발송 전 취소·환불 배송 동기화 (#233)")
    class CancelForOrder {

        @Test
        @DisplayName("PREPARING 배송이 있으면 CANCELLED 로 전이")
        void cancelsPreparingShipping() {
            Shipping shipping = preparingShipping();
            given(shippingRepository.findByOrderId(10L)).willReturn(Optional.of(shipping));

            shippingService.cancelForOrder(10L);

            assertThat(shipping.getStatus()).isEqualTo(ShippingStatus.CANCELLED);
        }

        @Test
        @DisplayName("SHIPPED 배송도 CANCELLED 로 전이 (#239 대비, 메서드 자체는 종착이 아니면 취소)")
        void cancelsShippedShipping() {
            Shipping shipping = preparingShipping();
            shipping.markShipped(CLOCK.instant());
            given(shippingRepository.findByOrderId(10L)).willReturn(Optional.of(shipping));

            shippingService.cancelForOrder(10L);

            assertThat(shipping.getStatus()).isEqualTo(ShippingStatus.CANCELLED);
        }

        @Test
        @DisplayName("배송이 없으면 무해 (no-op)")
        void noShipping_noop() {
            given(shippingRepository.findByOrderId(10L)).willReturn(Optional.empty());

            assertThatCode(() -> shippingService.cancelForOrder(10L)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("이미 종착(DELIVERED)인 배송은 건드리지 않는다")
        void terminalDelivered_noop() {
            Shipping shipping = preparingShipping();
            shipping.markShipped(CLOCK.instant());
            shipping.markDelivered(CLOCK.instant());
            given(shippingRepository.findByOrderId(10L)).willReturn(Optional.of(shipping));

            shippingService.cancelForOrder(10L);

            assertThat(shipping.getStatus()).isEqualTo(ShippingStatus.DELIVERED);
        }

        @Test
        @DisplayName("이미 CANCELLED 인 배송은 그대로 (중복 환불에 멱등)")
        void alreadyCancelled_noop() {
            Shipping shipping = preparingShipping();
            shipping.cancel();
            given(shippingRepository.findByOrderId(10L)).willReturn(Optional.of(shipping));

            shippingService.cancelForOrder(10L);

            assertThat(shipping.getStatus()).isEqualTo(ShippingStatus.CANCELLED);
        }
    }
}
