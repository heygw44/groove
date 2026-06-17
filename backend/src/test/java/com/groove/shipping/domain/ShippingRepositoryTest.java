package com.groove.shipping.domain;

import com.groove.common.persistence.JpaAuditingConfig;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderRepository;
import com.groove.support.OrderFixtures;
import com.groove.support.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Limit;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfig.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
@DisplayName("ShippingRepository PII 익명화 배치 조회 (#170 Part B)")
class ShippingRepositoryTest {

    @Autowired
    private ShippingRepository shippingRepository;

    @Autowired
    private OrderRepository orderRepository;

    private Shipping persistDeliveredShipping(String orderNumber) {
        Order order = orderRepository.saveAndFlush(
                OrderFixtures.guestOrder(orderNumber, "guest@example.com", "01099998888"));
        Shipping shipping = Shipping.prepare(order, order.getShippingInfo(), "trk-" + orderNumber);
        shipping.markShipped(Instant.now());
        shipping.markDelivered(Instant.now());
        return shippingRepository.saveAndFlush(shipping);
    }

    @Test
    @DisplayName("DELIVERED + delivered_at < cutoff + anonymized_at IS NULL 인 배송을 id 프로젝션으로 조회한다")
    void findsDeliveredUnanonymizedBeforeCutoff() {
        Shipping shipping = persistDeliveredShipping("ORD-ANON-1");
        Instant cutoff = shipping.getDeliveredAt().plusMillis(1);

        List<ShippingRepository.ShippingIdView> found =
                shippingRepository.findByStatusAndDeliveredAtBeforeAndAnonymizedAtIsNullOrderByDeliveredAtAsc(
                        ShippingStatus.DELIVERED, cutoff, Limit.of(200));

        assertThat(found).extracting(ShippingRepository.ShippingIdView::getId).contains(shipping.getId());
    }

    @Test
    @DisplayName("cutoff 가 delivered_at 이하이면 (보존기간 미경과) 제외된다")
    void excludesWhenDeliveredAtNotBeforeCutoff() {
        Shipping shipping = persistDeliveredShipping("ORD-ANON-2");
        // DB DATETIME(6)(마이크로초)에 맞춰 cutoff 를 마이크로초로 절단한다.
        Instant cutoff = shipping.getDeliveredAt().truncatedTo(ChronoUnit.MICROS);

        List<ShippingRepository.ShippingIdView> found =
                shippingRepository.findByStatusAndDeliveredAtBeforeAndAnonymizedAtIsNullOrderByDeliveredAtAsc(
                        ShippingStatus.DELIVERED, cutoff, Limit.of(200));

        assertThat(found).extracting(ShippingRepository.ShippingIdView::getId).doesNotContain(shipping.getId());
    }

    @Test
    @DisplayName("이미 익명화된(anonymized_at 있음) 배송은 제외된다")
    void excludesAlreadyAnonymized() {
        Shipping shipping = persistDeliveredShipping("ORD-ANON-3");
        Instant cutoff = shipping.getDeliveredAt().plusMillis(1);
        shipping.anonymizePii(Instant.now());
        shippingRepository.saveAndFlush(shipping);

        List<ShippingRepository.ShippingIdView> found =
                shippingRepository.findByStatusAndDeliveredAtBeforeAndAnonymizedAtIsNullOrderByDeliveredAtAsc(
                        ShippingStatus.DELIVERED, cutoff, Limit.of(200));

        assertThat(found).extracting(ShippingRepository.ShippingIdView::getId).doesNotContain(shipping.getId());
    }
}
