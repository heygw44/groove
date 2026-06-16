package com.groove.payment.application;

import com.groove.common.exception.DomainException;
import com.groove.member.domain.MemberRepository;
import com.groove.member.exception.MemberNotFoundException;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderStatus;
import com.groove.order.exception.IllegalStateTransitionException;
import com.groove.order.exception.OrderNotFoundException;
import com.groove.payment.api.dto.PaymentApiResponse;
import com.groove.payment.api.dto.PaymentCreateRequest;
import com.groove.payment.application.PaymentRequestSteps.PaymentRequestPrep;
import com.groove.payment.domain.Payment;
import com.groove.payment.domain.PaymentMethod;
import com.groove.payment.domain.PaymentRepository;
import com.groove.payment.domain.PaymentStatus;
import com.groove.payment.gateway.PaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 결제 요청 트랜잭션 단계 단위 테스트 (#237) — prepare 검증 분기와 persist 영속화. PG 호출은 트랜잭션 밖
 * (PaymentService 오케스트레이터)이므로 여기서는 다루지 않는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentRequestSteps")
class PaymentRequestStepsTest {

    private static final String ORDER_NUMBER = "ORD-20260512-A1B2C3";
    private static final long ORDER_AMOUNT = 35_000L;
    private static final long ORDER_ID = 10L;

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private MemberRepository memberRepository;

    private PaymentRequestSteps steps;

    @BeforeEach
    void setUp() {
        steps = new PaymentRequestSteps(paymentRepository, orderRepository, memberRepository);
        lenient().when(memberRepository.existsByIdAndDeletedAtIsNull(1L)).thenReturn(true);
    }

    private Order order(boolean guest, Long memberId, OrderStatus status) {
        Order order = Mockito.mock(Order.class, Mockito.withSettings().strictness(Strictness.LENIENT));
        when(order.getId()).thenReturn(ORDER_ID);
        when(order.getOrderNumber()).thenReturn(ORDER_NUMBER);
        when(order.getStatus()).thenReturn(status);
        when(order.getTotalAmount()).thenReturn(ORDER_AMOUNT);
        when(order.getPayableAmount()).thenReturn(ORDER_AMOUNT);
        when(order.isGuestOrder()).thenReturn(guest);
        when(order.getMemberId()).thenReturn(memberId);
        return order;
    }

    private PaymentCreateRequest request() {
        return new PaymentCreateRequest(ORDER_NUMBER, PaymentMethod.CARD);
    }

    @Test
    @DisplayName("prepare: 회원 본인 PENDING 주문 → proceed (orderId·orderNumber·payable)")
    void prepare_member_proceed() {
        Order order = order(false, 1L, OrderStatus.PENDING);
        when(orderRepository.findByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

        PaymentRequestPrep prep = steps.prepare(1L, request());

        assertThat(prep.isExisting()).isFalse();
        assertThat(prep.orderId()).isEqualTo(ORDER_ID);
        assertThat(prep.orderNumber()).isEqualTo(ORDER_NUMBER);
        assertThat(prep.payable()).isEqualTo(ORDER_AMOUNT);
    }

    @Test
    @DisplayName("prepare: 게스트 주문 → 익명 호출자도 proceed")
    void prepare_guestOrder_anonymousCaller_proceed() {
        Order order = order(true, null, OrderStatus.PENDING);
        when(orderRepository.findByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

        PaymentRequestPrep prep = steps.prepare(null, request());

        assertThat(prep.isExisting()).isFalse();
        assertThat(prep.orderId()).isEqualTo(ORDER_ID);
    }

    @Test
    @DisplayName("prepare: 쿠폰 적용 주문 → payable 로 진행 (#91)")
    void prepare_withCoupon_payable() {
        Order order = order(false, 1L, OrderStatus.PENDING);
        when(order.getTotalAmount()).thenReturn(50_000L);
        when(order.getPayableAmount()).thenReturn(30_000L);
        when(orderRepository.findByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

        PaymentRequestPrep prep = steps.prepare(1L, request());

        assertThat(prep.payable()).isEqualTo(30_000L);
    }

    @Test
    @DisplayName("prepare: 주문에 이미 결제가 있으면 existing 반환")
    void prepare_existingPayment_returnsExisting() {
        Order order = order(false, 1L, OrderStatus.PENDING);
        when(orderRepository.findByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.of(order));
        Payment existing = Payment.initiate(order, ORDER_AMOUNT, PaymentMethod.BANK_TRANSFER, "MOCK", "mock-tx-existing");
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existing));

        PaymentRequestPrep prep = steps.prepare(1L, request());

        assertThat(prep.isExisting()).isTrue();
        assertThat(prep.existingResponse().method()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(prep.existingResponse().status()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("prepare: 미존재 주문 → 404")
    void prepare_unknownOrder_throwsNotFound() {
        when(orderRepository.findByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> steps.prepare(1L, request()))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("prepare: 타 회원 주문 → 404")
    void prepare_memberOrder_otherCaller_throwsNotFound() {
        Order order = order(false, 1L, OrderStatus.PENDING);
        when(orderRepository.findByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> steps.prepare(2L, request()))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("prepare: 회원 주문에 익명 접근 → 404")
    void prepare_memberOrder_anonymousCaller_throwsNotFound() {
        Order order = order(false, 1L, OrderStatus.PENDING);
        when(orderRepository.findByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> steps.prepare(null, request()))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("prepare: PENDING 아닌 주문 → 409")
    void prepare_orderNotPending_throwsConflict() {
        Order order = order(false, 1L, OrderStatus.PAID);
        when(orderRepository.findByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> steps.prepare(1L, request()))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    @DisplayName("prepare: payable<=0 주문 → 422 DomainException (#91 전액할인 v1 미지원)")
    void prepare_zeroPayableOrder_throwsDomainException() {
        Order order = order(false, 1L, OrderStatus.PENDING);
        when(order.getPayableAmount()).thenReturn(0L);
        when(orderRepository.findByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> steps.prepare(1L, request()))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("prepare: 탈퇴(soft delete) 회원이 본인 PENDING 주문 결제 → 404, findByOrderId 미호출 (#187)")
    void prepare_memberWithdrawn_throwsNotFound() {
        long withdrawnMemberId = 7L;
        Order order = order(false, withdrawnMemberId, OrderStatus.PENDING);
        when(orderRepository.findByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.of(order));
        when(memberRepository.existsByIdAndDeletedAtIsNull(withdrawnMemberId)).thenReturn(false);

        assertThatThrownBy(() -> steps.prepare(withdrawnMemberId, request()))
                .isInstanceOf(MemberNotFoundException.class);

        verify(paymentRepository, never()).findByOrderId(any());
    }

    @Test
    @DisplayName("persist: PENDING 결제를 payable 로 저장 후 응답")
    void persist_savesPendingPayment() {
        Order order = order(false, 1L, OrderStatus.PENDING);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        PaymentRequestPrep prep = PaymentRequestPrep.proceed(ORDER_ID, ORDER_NUMBER, ORDER_AMOUNT);
        PaymentResponse pg = new PaymentResponse("mock-tx-1", PaymentStatus.PENDING, "MOCK");

        PaymentApiResponse response = steps.persist(prep, PaymentMethod.CARD, pg);

        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.amount()).isEqualTo(ORDER_AMOUNT);
        assertThat(response.method()).isEqualTo(PaymentMethod.CARD);
        assertThat(response.pgProvider()).isEqualTo("MOCK");

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPgTransactionId()).isEqualTo("mock-tx-1");
        assertThat(saved.getValue().getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("findExistingForOrder: 기존 결제가 있으면 응답 매핑, 없으면 empty")
    void findExistingForOrder() {
        Order order = order(false, 1L, OrderStatus.PENDING);
        Payment existing = Payment.initiate(order, ORDER_AMOUNT, PaymentMethod.CARD, "MOCK", "mock-tx-1");
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existing), Optional.empty());

        assertThat(steps.findExistingForOrder(ORDER_ID)).isPresent();
        assertThat(steps.findExistingForOrder(ORDER_ID)).isEmpty();
    }
}
