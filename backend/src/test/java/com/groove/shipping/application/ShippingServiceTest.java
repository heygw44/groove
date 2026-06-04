package com.groove.shipping.application;

import com.groove.shipping.api.dto.ShippingResponse;
import com.groove.shipping.domain.Shipping;
import com.groove.shipping.domain.ShippingRepository;
import com.groove.shipping.domain.ShippingStatus;
import com.groove.shipping.exception.ShippingNotFoundException;
import com.groove.support.OrderFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShippingService 단위 테스트")
class ShippingServiceTest {

    private static final String TRACKING = "8a4f0c2e-1234-4abc-9def-0123456789ab";

    @Mock
    private ShippingRepository shippingRepository;

    private ShippingService shippingService;

    @BeforeEach
    void setUp() {
        shippingService = new ShippingService(shippingRepository);
    }

    private Shipping preparingShipping() {
        return Shipping.prepare(OrderFixtures.memberOrder("ORD-1", 1L), OrderFixtures.sampleShippingInfo(), TRACKING);
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
    @DisplayName("advanceToShipped — PREPARING 이면 SHIPPED 로 전이")
    void advanceToShipped_fromPreparing() {
        Shipping shipping = preparingShipping();
        given(shippingRepository.findById(1L)).willReturn(Optional.of(shipping));

        shippingService.advanceToShipped(1L);

        assertThat(shipping.getStatus()).isEqualTo(ShippingStatus.SHIPPED);
    }

    @Test
    @DisplayName("advanceToShipped — 이미 SHIPPED 면 무시 (멱등)")
    void advanceToShipped_alreadyShipped_noop() {
        Shipping shipping = preparingShipping();
        shipping.markShipped();
        given(shippingRepository.findById(1L)).willReturn(Optional.of(shipping));

        shippingService.advanceToShipped(1L);

        assertThat(shipping.getStatus()).isEqualTo(ShippingStatus.SHIPPED);
    }

    @Test
    @DisplayName("advanceToShipped — 대상 없으면 무해")
    void advanceToShipped_missing_noop() {
        given(shippingRepository.findById(99L)).willReturn(Optional.empty());

        shippingService.advanceToShipped(99L);
        // 예외 없이 통과
    }

    @Test
    @DisplayName("advanceToDelivered — SHIPPED 면 DELIVERED 로 전이")
    void advanceToDelivered_fromShipped() {
        Shipping shipping = preparingShipping();
        shipping.markShipped();
        given(shippingRepository.findById(1L)).willReturn(Optional.of(shipping));

        shippingService.advanceToDelivered(1L);

        assertThat(shipping.getStatus()).isEqualTo(ShippingStatus.DELIVERED);
    }

    @Test
    @DisplayName("advanceToDelivered — PREPARING 이면 무시 (멱등)")
    void advanceToDelivered_fromPreparing_noop() {
        Shipping shipping = preparingShipping();
        given(shippingRepository.findById(1L)).willReturn(Optional.of(shipping));

        shippingService.advanceToDelivered(1L);

        assertThat(shipping.getStatus()).isEqualTo(ShippingStatus.PREPARING);
    }
}
