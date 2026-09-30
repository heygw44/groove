package com.groove.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.member.entity.Member;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderClaim;
import com.groove.order.entity.OrderClaimStatus;
import com.groove.order.entity.OrderItem;
import com.groove.order.entity.OrderItemClaimStatus;
import com.groove.order.entity.OrderItemStatus;
import com.groove.order.repository.OrderClaimRepository;
import com.groove.order.repository.OrderRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;

@ExtendWith(MockitoExtension.class)
class OrderClaimFinalizeServiceTest {

	private static final Long ORDER_ID = 10L;
	private static final Long ITEM_ID = 100L;
	private static final Long CLAIM_ID = 500L;
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 29, 12, 0);

	@Mock
	OrderClaimRepository orderClaimRepository;

	@Mock
	OrderRepository orderRepository;

	@Mock
	OrderCancelRestorer restorer;

	OrderClaimFinalizeService service;
	Order order;
	OrderItem item;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
		service = new OrderClaimFinalizeService(orderClaimRepository, orderRepository, restorer, clock);
		Member member = MemberFixture.create();
		Artist artist = ArtistFixture.withId(1L);
		Product product = ProductFixture.withId(ProductFixture.create(artist), 200L);
		order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 1), ORDER_ID);
		item = order.getItems().get(0);
		ReflectionTestUtils.setField(item, "id", ITEM_ID);
	}

	private void stubClaimLookup(OrderClaim claim) {
		given(orderClaimRepository.findOrderIdById(CLAIM_ID)).willReturn(Optional.of(ORDER_ID));
		given(orderClaimRepository.findById(CLAIM_ID)).willReturn(Optional.of(claim));
	}

	private void stubOrderLock() {
		given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
		given(orderRepository.findWithItemsById(ORDER_ID)).willReturn(Optional.of(order));
	}

	/** 클레임이 REQUESTED·COLLECTING(진행 중)이어서 실제로 주문을 다시 읽는 테스트에서만 스텁한다. */
	private void stubOrderLookup() {
		given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
		given(orderRepository.findWithItemsById(ORDER_ID)).willReturn(Optional.of(order));
	}

	@Nested
	@DisplayName("lockRefundableClaim()")
	class LockRefundableClaim {

		@Test
		@DisplayName("주문 락을 잡은 뒤 클레임을 다시 읽고 진행 중이면 통과한다")
		void locksOrderThenReloadsClaim() {
			// given
			OrderClaim claim = OrderClaim.requestCancel(item, "사유", null, NOW.minusMinutes(5));
			ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
			given(orderClaimRepository.findOrderIdById(CLAIM_ID)).willReturn(Optional.of(ORDER_ID));
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderClaimRepository.findById(CLAIM_ID)).willReturn(Optional.of(claim));

			// when
			service.lockRefundableClaim(CLAIM_ID);

			// then
			InOrder inOrder = inOrder(orderRepository, orderClaimRepository);
			inOrder.verify(orderRepository).findByIdForUpdate(ORDER_ID);
			inOrder.verify(orderClaimRepository).findById(CLAIM_ID);
		}

		@Test
		@DisplayName("COLLECTING 인 반품 클레임도 진행 중이라 통과한다")
		void passesForCollectingClaim() {
			// given
			OrderClaim claim = OrderClaim.requestReturn(item, "사유", NOW.minusMinutes(5));
			ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
			ReflectionTestUtils.setField(claim, "status", OrderClaimStatus.COLLECTING);
			given(orderClaimRepository.findOrderIdById(CLAIM_ID)).willReturn(Optional.of(ORDER_ID));
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderClaimRepository.findById(CLAIM_ID)).willReturn(Optional.of(claim));

			// when & then
			assertThatCode(() -> service.lockRefundableClaim(CLAIM_ID)).doesNotThrowAnyException();
		}

		@ParameterizedTest
		@EnumSource(value = OrderClaimStatus.class, names = {"DONE", "REJECTED", "WITHDRAWN"})
		@DisplayName("이미 종결된 클레임이면 ORDER_CLAIM_NOT_ALLOWED 예외를 던진다")
		void throwsWhenClaimAlreadyClosed(OrderClaimStatus status) {
			// given
			OrderClaim claim = OrderClaim.requestCancel(item, "사유", null, NOW.minusMinutes(5));
			ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
			ReflectionTestUtils.setField(claim, "status", status);
			given(orderClaimRepository.findOrderIdById(CLAIM_ID)).willReturn(Optional.of(ORDER_ID));
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderClaimRepository.findById(CLAIM_ID)).willReturn(Optional.of(claim));

			// when & then
			assertThatThrownBy(() -> service.lockRefundableClaim(CLAIM_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_CLAIM_NOT_ALLOWED);
		}

		@Test
		@DisplayName("클레임이 없으면 COMMON_RESOURCE_NOT_FOUND 예외를 던진다")
		void throwsWhenClaimMissing() {
			// given
			given(orderClaimRepository.findOrderIdById(CLAIM_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> service.lockRefundableClaim(CLAIM_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_RESOURCE_NOT_FOUND);
		}

		@Test
		@DisplayName("락 뒤에 클레임이 사라졌으면 COMMON_RESOURCE_NOT_FOUND 예외를 던진다")
		void throwsWhenClaimDeletedAfterLock() {
			// given
			given(orderClaimRepository.findOrderIdById(CLAIM_ID)).willReturn(Optional.of(ORDER_ID));
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderClaimRepository.findById(CLAIM_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> service.lockRefundableClaim(CLAIM_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_RESOURCE_NOT_FOUND);
		}

		@Test
		@DisplayName("주문이 없으면 ORDER_NOT_FOUND 예외를 던진다")
		void throwsWhenOrderMissing() {
			// given
			given(orderClaimRepository.findOrderIdById(CLAIM_ID)).willReturn(Optional.of(ORDER_ID));
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> service.lockRefundableClaim(CLAIM_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_NOT_FOUND);
		}
	}

	@Nested
	@DisplayName("applyRefundDone()")
	class ApplyRefundDone {

		@Test
		@DisplayName("CANCEL 클레임이면 승인 처리하고 재고를 복원한다")
		void finalizesCancelClaim() {
			// given
			ReflectionTestUtils.setField(item, "status", OrderItemStatus.PAID);
			ReflectionTestUtils.setField(item, "claimStatus", OrderItemClaimStatus.CANCEL_REQUEST);
			OrderClaim claim = OrderClaim.requestCancel(item, "사유", null, NOW.minusMinutes(5));
			ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
			stubClaimLookup(claim);
			stubOrderLookup();

			// when
			service.applyRefundDone(CLAIM_ID, NOW);

			// then
			assertThat(claim.getStatus()).isEqualTo(OrderClaimStatus.DONE);
			assertThat(item.getStatus()).isEqualTo(OrderItemStatus.CANCELED);
			assertThat(item.getClaimStatus()).isEqualTo(OrderItemClaimStatus.CANCEL_DONE);
			verify(restorer).restoreItems(order, List.of(item), true, true);
		}

		@Test
		@DisplayName("RETURN 클레임이고 재입고를 선택했으면 재고를 복원한다")
		void finalizesReturnClaimWithRestock() {
			// given
			ReflectionTestUtils.setField(item, "status", OrderItemStatus.DELIVERED);
			ReflectionTestUtils.setField(item, "claimStatus", OrderItemClaimStatus.COLLECTING);
			OrderClaim claim = OrderClaim.requestReturn(item, "사유", NOW.minusMinutes(10));
			ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
			ReflectionTestUtils.setField(claim, "status", OrderClaimStatus.COLLECTING);
			ReflectionTestUtils.setField(claim, "restock", true);
			stubClaimLookup(claim);
			stubOrderLookup();

			// when
			service.applyRefundDone(CLAIM_ID, NOW);

			// then
			assertThat(claim.getStatus()).isEqualTo(OrderClaimStatus.DONE);
			assertThat(item.getStatus()).isEqualTo(OrderItemStatus.RETURNED);
			assertThat(item.getClaimStatus()).isEqualTo(OrderItemClaimStatus.RETURN_DONE);
			verify(restorer).restoreItems(order, List.of(item), true, true);
		}

		@Test
		@DisplayName("RETURN 클레임이고 재입고를 선택하지 않았으면 재고는 그대로 둔다")
		void finalizesReturnClaimWithoutRestock() {
			// given
			ReflectionTestUtils.setField(item, "status", OrderItemStatus.DELIVERED);
			ReflectionTestUtils.setField(item, "claimStatus", OrderItemClaimStatus.COLLECTING);
			OrderClaim claim = OrderClaim.requestReturn(item, "사유", NOW.minusMinutes(10));
			ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
			ReflectionTestUtils.setField(claim, "status", OrderClaimStatus.COLLECTING);
			ReflectionTestUtils.setField(claim, "restock", false);
			stubClaimLookup(claim);
			stubOrderLookup();

			// when
			service.applyRefundDone(CLAIM_ID, NOW);

			// then
			verify(restorer).restoreItems(order, List.of(item), false, true);
		}

		@Test
		@DisplayName("이미 종결된 클레임이면 아무 것도 하지 않는다(멱등)")
		void doesNothingWhenAlreadyResolved() {
			// given
			OrderClaim claim = OrderClaim.requestCancel(item, "사유", null, NOW.minusMinutes(5));
			ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
			ReflectionTestUtils.setField(claim, "status", OrderClaimStatus.DONE);
			stubClaimLookup(claim);
			stubOrderLock();

			// when
			service.applyRefundDone(CLAIM_ID, NOW);

			// then
			verify(restorer, never()).restoreItems(any(), any(), eq(true), eq(true));
		}

		@Test
		@DisplayName("주문 락을 잡은 뒤에 클레임을 읽는다")
		void locksOrderBeforeLoadingClaim() {
			// given
			ReflectionTestUtils.setField(item, "status", OrderItemStatus.PAID);
			ReflectionTestUtils.setField(item, "claimStatus", OrderItemClaimStatus.CANCEL_REQUEST);
			OrderClaim claim = OrderClaim.requestCancel(item, "사유", null, NOW.minusMinutes(5));
			ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
			stubClaimLookup(claim);
			stubOrderLookup();

			// when
			service.applyRefundDone(CLAIM_ID, NOW);

			// then
			InOrder inOrder = inOrder(orderClaimRepository, orderRepository);
			inOrder.verify(orderClaimRepository).findOrderIdById(CLAIM_ID);
			inOrder.verify(orderRepository).findByIdForUpdate(ORDER_ID);
			inOrder.verify(orderClaimRepository).findById(CLAIM_ID);
		}

		@Test
		@DisplayName("클레임이 없으면 주문을 잠그지 않고 돌아간다")
		void returnsWhenClaimMissing() {
			// given
			given(orderClaimRepository.findOrderIdById(CLAIM_ID)).willReturn(Optional.empty());

			// when
			service.applyRefundDone(CLAIM_ID, NOW);

			// then
			verify(orderRepository, never()).findByIdForUpdate(any());
			verify(orderClaimRepository, never()).findById(any());
		}

		@Test
		@DisplayName("락 뒤에 클레임이 사라졌으면 아무 것도 하지 않는다")
		void returnsWhenClaimDeletedAfterLock() {
			// given
			given(orderClaimRepository.findOrderIdById(CLAIM_ID)).willReturn(Optional.of(ORDER_ID));
			stubOrderLock();
			given(orderClaimRepository.findById(CLAIM_ID)).willReturn(Optional.empty());

			// when
			service.applyRefundDone(CLAIM_ID, NOW);

			// then
			verify(restorer, never()).restoreItems(any(), any(), eq(true), eq(true));
		}
	}

	@Nested
	@DisplayName("applyRefundFailed()")
	class ApplyRefundFailed {

		@Test
		@DisplayName("CANCEL 클레임이 거절되면 거부 표시를 남기고 상품주문 상태는 그대로 둔다")
		void rejectsCancelClaim() {
			// given
			ReflectionTestUtils.setField(item, "status", OrderItemStatus.PAID);
			ReflectionTestUtils.setField(item, "claimStatus", OrderItemClaimStatus.CANCEL_REQUEST);
			OrderClaim claim = OrderClaim.requestCancel(item, "사유", null, NOW.minusMinutes(5));
			ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
			stubClaimLookup(claim);
			stubOrderLookup();

			// when
			service.applyRefundFailed(CLAIM_ID);

			// then
			assertThat(claim.getStatus()).isEqualTo(OrderClaimStatus.REJECTED);
			assertThat(item.getStatus()).isEqualTo(OrderItemStatus.PAID);
			assertThat(item.getClaimStatus()).isEqualTo(OrderItemClaimStatus.CANCEL_REJECT);
		}

		@Test
		@DisplayName("이미 종결된 클레임이면 아무 것도 하지 않는다")
		void doesNothingWhenAlreadyResolved() {
			// given
			OrderClaim claim = OrderClaim.requestCancel(item, "사유", null, NOW.minusMinutes(5));
			ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
			ReflectionTestUtils.setField(claim, "status", OrderClaimStatus.WITHDRAWN);
			stubClaimLookup(claim);
			stubOrderLock();

			// when
			service.applyRefundFailed(CLAIM_ID);

			// then
			assertThat(claim.getStatus()).isEqualTo(OrderClaimStatus.WITHDRAWN);
		}

		@Test
		@DisplayName("주문 락을 잡은 뒤에 클레임을 읽는다")
		void locksOrderBeforeLoadingClaim() {
			// given
			ReflectionTestUtils.setField(item, "status", OrderItemStatus.PAID);
			ReflectionTestUtils.setField(item, "claimStatus", OrderItemClaimStatus.CANCEL_REQUEST);
			OrderClaim claim = OrderClaim.requestCancel(item, "사유", null, NOW.minusMinutes(5));
			ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
			stubClaimLookup(claim);
			stubOrderLookup();

			// when
			service.applyRefundFailed(CLAIM_ID);

			// then
			InOrder inOrder = inOrder(orderClaimRepository, orderRepository);
			inOrder.verify(orderClaimRepository).findOrderIdById(CLAIM_ID);
			inOrder.verify(orderRepository).findByIdForUpdate(ORDER_ID);
			inOrder.verify(orderClaimRepository).findById(CLAIM_ID);
		}

		@Test
		@DisplayName("클레임이 없으면 주문을 잠그지 않고 돌아간다")
		void returnsWhenClaimMissing() {
			// given
			given(orderClaimRepository.findOrderIdById(CLAIM_ID)).willReturn(Optional.empty());

			// when
			service.applyRefundFailed(CLAIM_ID);

			// then
			verify(orderRepository, never()).findByIdForUpdate(any());
			verify(orderClaimRepository, never()).findById(any());
		}

		@Test
		@DisplayName("락 뒤에 클레임이 사라졌으면 아무 것도 하지 않는다")
		void returnsWhenClaimDeletedAfterLock() {
			// given
			given(orderClaimRepository.findOrderIdById(CLAIM_ID)).willReturn(Optional.of(ORDER_ID));
			stubOrderLock();
			given(orderClaimRepository.findById(CLAIM_ID)).willReturn(Optional.empty());

			// when
			service.applyRefundFailed(CLAIM_ID);

			// then
			assertThat(item.getClaimStatus()).isNull();
		}
	}
}
