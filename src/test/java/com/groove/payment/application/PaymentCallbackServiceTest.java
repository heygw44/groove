package com.groove.payment.application;

import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumFormat;
import com.groove.catalog.album.domain.AlbumStatus;
import com.groove.catalog.artist.domain.Artist;
import com.groove.catalog.genre.domain.Genre;
import com.groove.catalog.label.domain.Label;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderItem;
import com.groove.order.domain.OrderStatus;
import com.groove.order.event.OrderPaidEvent;
import com.groove.payment.api.dto.PaymentCallbackResult;
import com.groove.payment.domain.Payment;
import com.groove.payment.domain.PaymentMethod;
import com.groove.payment.domain.PaymentRepository;
import com.groove.payment.domain.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentCallbackService 단위 테스트")
class PaymentCallbackServiceTest {

    private static final String PG_TX = "mock-tx-1";
    private static final String ORDER_NUMBER = "ORD-20260512-A1B2C3";

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PaymentCallbackService service;

    @BeforeEach
    void setUp() {
        service = new PaymentCallbackService(paymentRepository, eventPublisher);
    }

    private static Album album(int stock) {
        return Album.create("Abbey Road", Artist.create("The Beatles", null), Genre.create("Rock"),
                Label.create("Apple Records"), (short) 1969, AlbumFormat.LP_12, 35000L, stock,
                AlbumStatus.SELLING, false, null, null);
    }

    private static Payment pendingPayment(Album album, int quantity) {
        Order order = Order.placeForMember(ORDER_NUMBER, 1L);
        order.addItem(OrderItem.create(album, quantity));
        return Payment.initiate(order, order.getTotalAmount(), PaymentMethod.CARD, "MOCK", PG_TX);
    }

    @Test
    @DisplayName("PAID: 결제·주문을 확정하고 OrderPaidEvent 를 발행한다 (재고 불변)")
    void applyResult_paid_confirmsAndPublishesEvent() {
        Album album = album(99);
        Payment payment = pendingPayment(album, 1);
        ReflectionTestUtils.setField(payment, "id", 42L); // 단위 테스트라 미영속 — paymentId 단언이 공허해지지 않게 고정값 주입
        given(paymentRepository.findWithOrderAndItemsByPgTransactionId(PG_TX)).willReturn(Optional.of(payment));

        PaymentCallbackResult result = service.applyResult(PG_TX, PaymentStatus.PAID, null);

        assertThat(result.outcome()).isEqualTo(PaymentCallbackResult.Outcome.APPLIED);
        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getPaidAt()).isNotNull();
        assertThat(payment.getOrder().getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(album.getStock()).isEqualTo(99);

        ArgumentCaptor<OrderPaidEvent> event = ArgumentCaptor.forClass(OrderPaidEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().orderNumber()).isEqualTo(ORDER_NUMBER);
        assertThat(event.getValue().memberId()).isEqualTo(1L);
        assertThat(event.getValue().paymentId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("FAILED: 보상 트랜잭션 — 결제 실패·주문 결제실패·재고 복원, 이벤트 미발행")
    void applyResult_failed_compensates() {
        Album album = album(98); // 주문이 2개 차감했다고 가정
        Payment payment = pendingPayment(album, 2);
        given(paymentRepository.findWithOrderAndItemsByPgTransactionId(PG_TX)).willReturn(Optional.of(payment));

        PaymentCallbackResult result = service.applyResult(PG_TX, PaymentStatus.FAILED, "카드 한도 초과");

        assertThat(result.outcome()).isEqualTo(PaymentCallbackResult.Outcome.APPLIED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("카드 한도 초과");
        assertThat(payment.getOrder().getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(album.getStock()).isEqualTo(100); // 98 + 2 복원
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("FAILED + 사유 null: 기본 사유를 기록한다")
    void applyResult_failed_nullReason_usesDefault() {
        Payment payment = pendingPayment(album(100), 1);
        given(paymentRepository.findWithOrderAndItemsByPgTransactionId(PG_TX)).willReturn(Optional.of(payment));

        service.applyResult(PG_TX, PaymentStatus.FAILED, null);

        assertThat(payment.getFailureReason()).isNotBlank();
    }

    @Test
    @DisplayName("알 수 없는 거래: IGNORED, 아무 것도 바꾸지 않는다")
    void applyResult_unknownTransaction_ignored() {
        given(paymentRepository.findWithOrderAndItemsByPgTransactionId("nope")).willReturn(Optional.empty());

        PaymentCallbackResult result = service.applyResult("nope", PaymentStatus.PAID, null);

        assertThat(result.outcome()).isEqualTo(PaymentCallbackResult.Outcome.IGNORED);
        assertThat(result.paymentId()).isNull();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("이미 처리된 결제: ALREADY_PROCESSED, 상태 전이·재고 복원 없음")
    void applyResult_alreadyProcessed_noop() {
        Album album = album(99);
        Payment payment = pendingPayment(album, 1);
        payment.markPaid(); // 이미 PAID
        given(paymentRepository.findWithOrderAndItemsByPgTransactionId(PG_TX)).willReturn(Optional.of(payment));

        PaymentCallbackResult result = service.applyResult(PG_TX, PaymentStatus.FAILED, "늦게 도착한 실패 통보");

        assertThat(result.outcome()).isEqualTo(PaymentCallbackResult.Outcome.ALREADY_PROCESSED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(album.getStock()).isEqualTo(99);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("결과가 PAID/FAILED 가 아니면 거부한다 (방어선)")
    void applyResult_invalidResult_rejected() {
        assertThatThrownBy(() -> service.applyResult(PG_TX, PaymentStatus.PENDING, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.applyResult(PG_TX, PaymentStatus.REFUNDED, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(paymentRepository, eventPublisher);
    }
}
