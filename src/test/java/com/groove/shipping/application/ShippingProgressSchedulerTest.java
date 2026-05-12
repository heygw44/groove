package com.groove.shipping.application;

import com.groove.order.domain.Order;
import com.groove.shipping.domain.Shipping;
import com.groove.shipping.domain.ShippingRepository;
import com.groove.shipping.domain.ShippingStatus;
import com.groove.support.OrderFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShippingProgressScheduler — 자동 진행 (틱당 1단계)")
class ShippingProgressSchedulerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-12T00:00:00Z"), ZoneOffset.UTC);
    private static final Duration DELAY = Duration.ofSeconds(5);

    @Mock
    private ShippingRepository shippingRepository;
    @Mock
    private ShippingService shippingService;

    private ShippingProgressScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ShippingProgressScheduler(shippingRepository, shippingService, CLOCK, DELAY, DELAY, 200);
    }

    private Shipping shippingWithId(long id) {
        Order order = OrderFixtures.memberOrder("ORD-" + id, id);
        Shipping shipping = Shipping.prepare(order, OrderFixtures.sampleShippingInfo(), "tracking-" + id);
        ReflectionTestUtils.setField(shipping, "id", id);
        return shipping;
    }

    @Test
    @DisplayName("PREPARING 대상은 advanceToShipped, SHIPPED 대상은 advanceToDelivered 로 한 단계씩")
    void advancesEachCandidateOneStep() {
        given(shippingRepository.findByStatusAndCreatedAtBefore(eq(ShippingStatus.PREPARING), any(Instant.class), any(Limit.class)))
                .willReturn(List.of(shippingWithId(1L), shippingWithId(2L)));
        given(shippingRepository.findByStatusAndShippedAtBefore(eq(ShippingStatus.SHIPPED), any(Instant.class), any(Limit.class)))
                .willReturn(List.of(shippingWithId(3L)));

        scheduler.progressShipments();

        verify(shippingService).advanceToShipped(1L);
        verify(shippingService).advanceToShipped(2L);
        verify(shippingService).advanceToDelivered(3L);
        verifyNoMoreInteractions(shippingService);
    }

    @Test
    @DisplayName("대상이 없으면 ShippingService 를 호출하지 않는다")
    void noCandidates_noop() {
        given(shippingRepository.findByStatusAndCreatedAtBefore(eq(ShippingStatus.PREPARING), any(Instant.class), any(Limit.class)))
                .willReturn(List.of());
        given(shippingRepository.findByStatusAndShippedAtBefore(eq(ShippingStatus.SHIPPED), any(Instant.class), any(Limit.class)))
                .willReturn(List.of());

        scheduler.progressShipments();

        verifyNoMoreInteractions(shippingService);
    }

    @Test
    @DisplayName("한 건 실패해도 배치의 나머지는 계속 진행한다")
    void perItemFailureIsIsolated() {
        given(shippingRepository.findByStatusAndCreatedAtBefore(eq(ShippingStatus.PREPARING), any(Instant.class), any(Limit.class)))
                .willReturn(List.of(shippingWithId(1L), shippingWithId(2L)));
        given(shippingRepository.findByStatusAndShippedAtBefore(eq(ShippingStatus.SHIPPED), any(Instant.class), any(Limit.class)))
                .willReturn(List.of());
        willThrow(new RuntimeException("DB hiccup")).given(shippingService).advanceToShipped(1L);

        scheduler.progressShipments();

        verify(shippingService).advanceToShipped(1L);
        verify(shippingService).advanceToShipped(2L);
    }

    @Test
    @DisplayName("batch-size 가 0 이하면 생성 시점에 거부")
    void rejectsNonPositiveBatchSize() {
        assertThatThrownBy(() -> new ShippingProgressScheduler(shippingRepository, shippingService, CLOCK, DELAY, DELAY, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
