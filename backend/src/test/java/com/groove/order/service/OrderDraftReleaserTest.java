package com.groove.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.PaymentFixture;
import com.groove.fixture.ProductFixture;
import com.groove.member.entity.Member;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderSource;
import com.groove.order.entity.OrderStatus;
import com.groove.order.repository.OrderRepository;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;

@ExtendWith(MockitoExtension.class)
class OrderDraftReleaserTest {

	private static final Long MEMBER_ID = 1L;
	private static final Long ORDER_ID = 500L;

	@Mock
	private OrderRepository orderRepository;

	@Mock
	private OrderCancelRestorer orderCancelRestorer;

	@Mock
	private PaymentRepository paymentRepository;

	private OrderDraftReleaser releaser;

	private Member member;
	private Product product;
	private LocalDateTime now;

	@BeforeEach
	void setUp() {
		releaser = new OrderDraftReleaser(orderRepository, orderCancelRestorer, paymentRepository);

		Clock clock = Clock.fixed(Instant.parse("2026-09-04T03:00:00Z"), ZoneId.of("Asia/Seoul"));
		now = LocalDateTime.now(clock);
		member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
		Artist artist = ArtistFixture.withId(1L);
		product = ProductFixture.withId(ProductFixture.create(artist), 100L);
	}

	@Nested
	@DisplayName("releaseDrafts()")
	class ReleaseDrafts {

		@Test
		@DisplayName("해제 대상 주문을 SUPERSEDED 로 취소하고 재고·쿠폰을 복원한다")
		void supersedesAndRestoresEligibleDraft() {
			// given
			Order order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 1), ORDER_ID);
			given(orderRepository.findDraftIdsToSupersede(MEMBER_ID, OrderStatus.PENDING, OrderSource.LIMITED,
					PaymentStatus.UNRESOLVED)).willReturn(List.of(ORDER_ID));
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			// when
			releaser.releaseDrafts(MEMBER_ID, now);

			// then
			assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
			assertThat(order.getCancelReason()).isEqualTo(Order.SUPERSEDED_CANCEL_REASON);
			verify(orderCancelRestorer).restore(order, false);
		}

		@Test
		@DisplayName("조회 조건을 회원·PENDING·한정반 제외·미해소 결제 상태로 전달한다")
		void queriesWithMemberPendingAndUnresolvedPaymentConditions() {
			// given
			given(orderRepository.findDraftIdsToSupersede(MEMBER_ID, OrderStatus.PENDING, OrderSource.LIMITED,
					PaymentStatus.UNRESOLVED)).willReturn(List.of());

			// when
			releaser.releaseDrafts(MEMBER_ID, now);

			// then
			verify(orderRepository).findDraftIdsToSupersede(MEMBER_ID, OrderStatus.PENDING, OrderSource.LIMITED,
					PaymentStatus.UNRESOLVED);
			verify(orderCancelRestorer, never()).restore(any(), anyBoolean());
		}

		@Test
		@DisplayName("잠근 뒤 이미 확정됐으면 건드리지 않는다")
		void skipsWhenAlreadyPlacedAfterLock() {
			// given
			Order order = OrderFixture.withId(OrderFixture.place(OrderFixture.createWithItem(member, product, 1)),
					ORDER_ID);
			given(orderRepository.findDraftIdsToSupersede(MEMBER_ID, OrderStatus.PENDING, OrderSource.LIMITED,
					PaymentStatus.UNRESOLVED)).willReturn(List.of(ORDER_ID));
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			// when
			releaser.releaseDrafts(MEMBER_ID, now);

			// then
			assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
			verify(orderCancelRestorer, never()).restore(any(), anyBoolean());
		}

		@Test
		@DisplayName("잠근 사이 결제 승인이 시작돼 결제가 READY 면 건드리지 않는다")
		void skipsWhenPaymentBecameUnresolvedAfterLock() {
			// given
			Order order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 1), ORDER_ID);
			Payment payment = Payment.ready(order);
			given(orderRepository.findDraftIdsToSupersede(MEMBER_ID, OrderStatus.PENDING, OrderSource.LIMITED,
					PaymentStatus.UNRESOLVED)).willReturn(List.of(ORDER_ID));
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(payment));

			// when
			releaser.releaseDrafts(MEMBER_ID, now);

			// then
			assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
			verify(orderCancelRestorer, never()).restore(any(), anyBoolean());
		}

		@Test
		@DisplayName("결제가 실패로 끝난 주문은 해제한다")
		void supersedesWhenPaymentFailed() {
			// given
			Order order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 1), ORDER_ID);
			given(orderRepository.findDraftIdsToSupersede(MEMBER_ID, OrderStatus.PENDING, OrderSource.LIMITED,
					PaymentStatus.UNRESOLVED)).willReturn(List.of(ORDER_ID));
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(ORDER_ID))
					.willReturn(Optional.of(PaymentFixture.failed(order, "카드 한도 초과")));

			// when
			releaser.releaseDrafts(MEMBER_ID, now);

			// then
			assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
			verify(orderCancelRestorer).restore(order, false);
		}

		@Test
		@DisplayName("잠근 뒤 이미 PENDING 이 아니면 건드리지 않는다")
		void skipsWhenNoLongerPendingAfterLock() {
			// given
			Order order = OrderFixture.withId(OrderFixture.markPaid(OrderFixture.createWithItem(member, product, 1)),
					ORDER_ID);
			given(orderRepository.findDraftIdsToSupersede(MEMBER_ID, OrderStatus.PENDING, OrderSource.LIMITED,
					PaymentStatus.UNRESOLVED)).willReturn(List.of(ORDER_ID));
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			// when
			releaser.releaseDrafts(MEMBER_ID, now);

			// then
			verify(orderCancelRestorer, never()).restore(any(), anyBoolean());
		}

		@Test
		@DisplayName("잠근 뒤 확인한 출처가 한정반이면 건드리지 않는다")
		void skipsWhenOrderSourceIsLimitedAfterLock() {
			// given
			Order order = OrderFixture.withId(OrderFixture.create(member, "20260903-LTD001", OrderSource.LIMITED),
					ORDER_ID);
			order.addItem(product, 1);
			given(orderRepository.findDraftIdsToSupersede(MEMBER_ID, OrderStatus.PENDING, OrderSource.LIMITED,
					PaymentStatus.UNRESOLVED)).willReturn(List.of(ORDER_ID));
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			// when
			releaser.releaseDrafts(MEMBER_ID, now);

			// then
			assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
			verify(orderCancelRestorer, never()).restore(any(), anyBoolean());
		}

		@Test
		@DisplayName("잠근 사이 주문이 사라졌으면 건드리지 않는다")
		void skipsWhenOrderNotFoundAfterLock() {
			// given
			given(orderRepository.findDraftIdsToSupersede(MEMBER_ID, OrderStatus.PENDING, OrderSource.LIMITED,
					PaymentStatus.UNRESOLVED)).willReturn(List.of(ORDER_ID));
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.empty());

			// when
			releaser.releaseDrafts(MEMBER_ID, now);

			// then
			verify(orderCancelRestorer, never()).restore(any(), anyBoolean());
		}
	}
}
