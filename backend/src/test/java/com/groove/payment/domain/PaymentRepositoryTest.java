package com.groove.payment.domain;

import com.groove.common.persistence.JpaAuditingConfig;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderRepository;
import com.groove.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfig.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
@DisplayName("PaymentRepository 통합 테스트 (Testcontainers MySQL)")
class PaymentRepositoryTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;

    /**
     * 다른 통합 테스트(@SpringBootTest)가 커밋한 잔여 행을 제거하고 시작한다.
     * FK 의존 순서대로 payment 를 먼저, 그 다음 orders 를 비운다.
     * 본 클래스의 @DataJpaTest 는 트랜잭션 자동 롤백이라 외부에 영향을 주지 않는다.
     */
    @BeforeEach
    void cleanUp() {
        paymentRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
    }

    private Order persistGuestOrder(String orderNumber) {
        return orderRepository.saveAndFlush(Order.placeForGuest(orderNumber, "guest@example.com", null, com.groove.support.OrderFixtures.sampleShippingInfo()));
    }

    @Test
    @DisplayName("save → findById, 필드 round-trip + created_at 자동 채움")
    void save_and_findById() {
        Order order = persistGuestOrder("ORD-20260512-AAA111");

        Payment saved = paymentRepository.saveAndFlush(
                Payment.initiate(order, 35000L, PaymentMethod.CARD, "MOCK", "mock-tx-1"));

        Optional<Payment> found = paymentRepository.findById(saved.getId());
        assertThat(found).isPresent();
        Payment payment = found.get();
        assertThat(payment.getOrder().getId()).isEqualTo(order.getId());
        assertThat(payment.getAmount()).isEqualTo(35000L);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(payment.getPgProvider()).isEqualTo("MOCK");
        assertThat(payment.getPgTransactionId()).isEqualTo("mock-tx-1");
        assertThat(payment.getPaidAt()).isNull();
        assertThat(payment.getFailureReason()).isNull();
        assertThat(payment.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("findByOrderId: 주문에 접수된 결제 조회, 없으면 empty")
    void findByOrderId() {
        Order order = persistGuestOrder("ORD-20260512-BBB222");
        paymentRepository.saveAndFlush(Payment.initiate(order, 1000L, PaymentMethod.MOCK, "MOCK", "tx-2"));

        assertThat(paymentRepository.findByOrderId(order.getId())).isPresent();
        assertThat(paymentRepository.findByOrderId(-1L)).isEmpty();
    }

    @Test
    @DisplayName("uk_payment_order: 한 주문에 결제 2건 저장 시 제약 위반")
    void uniqueOrder_secondPaymentRejected() {
        Order order = persistGuestOrder("ORD-20260512-CCC333");
        paymentRepository.saveAndFlush(Payment.initiate(order, 1000L, PaymentMethod.MOCK, "MOCK", "tx-a"));

        assertThatThrownBy(() ->
                paymentRepository.saveAndFlush(Payment.initiate(order, 2000L, PaymentMethod.MOCK, "MOCK", "tx-b")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
