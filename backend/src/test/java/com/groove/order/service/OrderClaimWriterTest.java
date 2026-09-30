package com.groove.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class OrderClaimWriterTest {

	private static final Long MEMBER_ID = 1L;
	private static final Long ORDER_ID = 10L;
	private static final Long ITEM_ID = 100L;
	private static final Long CLAIM_ID = 500L;
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 29, 12, 0);

	@Mock
	OrderRepository orderRepository;

	@Mock
	OrderClaimRepository orderClaimRepository;

	@Mock
	OrderClaimRefundReader refundReader;

	OrderClaimWriter writer;
	Order order;
	OrderItem item;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
		writer = new OrderClaimWriter(orderRepository, orderClaimRepository, refundReader, clock);
		Member member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
		Artist artist = ArtistFixture.withId(1L);
		Product product = ProductFixture.withId(ProductFixture.create(artist), 200L);
		order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 2), ORDER_ID);
		item = order.getItems().get(0);
		ReflectionTestUtils.setField(item, "id", ITEM_ID);
	}

	private void stubLockedClaim(OrderClaim claim) {
		given(orderClaimRepository.findOrderIdById(CLAIM_ID)).willReturn(Optional.of(ORDER_ID));
		given(orderClaimRepository.findWithOrderItemById(CLAIM_ID)).willReturn(Optional.of(claim));
		given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
	}

	private void stubSave() {
		given(orderClaimRepository.save(any(OrderClaim.class))).willAnswer(invocation -> {
			OrderClaim claim = invocation.getArgument(0);
			ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
			return claim;
		});
	}

	@Nested
	@DisplayName("requestCancel()")
	class RequestCancel {

		@Test
		@DisplayName("PAID 상품주문이면 즉시 취소 대상으로 표시한다")
		void marksImmediateWhenPaid() {
			// given
			ReflectionTestUtils.setField(item, "status", OrderItemStatus.PAID);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderRepository.findWithItemsByIdAndMemberId(ORDER_ID, MEMBER_ID)).willReturn(Optional.of(order));
			stubSave();

			// when
			OrderClaimRequestResult result = writer.requestCancel(MEMBER_ID, ORDER_ID, ITEM_ID, "사유", null);

			// then
			assertThat(result.immediate()).isTrue();
			assertThat(item.getClaimStatus()).isEqualTo(OrderItemClaimStatus.CANCEL_REQUEST);
		}

		@Test
		@DisplayName("PREPARING 상품주문이면 승인 대기 요청으로 남긴다")
		void marksPendingWhenPreparing() {
			// given
			ReflectionTestUtils.setField(item, "status", OrderItemStatus.PREPARING);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderRepository.findWithItemsByIdAndMemberId(ORDER_ID, MEMBER_ID)).willReturn(Optional.of(order));
			stubSave();

			// when
			OrderClaimRequestResult result = writer.requestCancel(MEMBER_ID, ORDER_ID, ITEM_ID, "사유", null);

			// then
			assertThat(result.immediate()).isFalse();
			assertThat(item.getClaimStatus()).isEqualTo(OrderItemClaimStatus.CANCEL_REQUEST);
		}

		@Test
		@DisplayName("이미 진행 중인 클레임이 있으면 ORDER_CLAIM_IN_PROGRESS 예외를 던진다")
		void throwsWhenClaimAlreadyInProgress() {
			// given
			ReflectionTestUtils.setField(item, "status", OrderItemStatus.PAID);
			ReflectionTestUtils.setField(item, "claimStatus", OrderItemClaimStatus.CANCEL_REQUEST);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderRepository.findWithItemsByIdAndMemberId(ORDER_ID, MEMBER_ID)).willReturn(Optional.of(order));

			// when & then
			assertThatThrownBy(() -> writer.requestCancel(MEMBER_ID, ORDER_ID, ITEM_ID, "사유", null))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_CLAIM_IN_PROGRESS);
		}

		@Test
		@DisplayName("SHIPPING 이후 상품주문이면 ORDER_CLAIM_NOT_ALLOWED 예외를 던진다")
		void throwsWhenNotAllowed() {
			// given
			ReflectionTestUtils.setField(item, "status", OrderItemStatus.SHIPPING);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderRepository.findWithItemsByIdAndMemberId(ORDER_ID, MEMBER_ID)).willReturn(Optional.of(order));

			// when & then
			assertThatThrownBy(() -> writer.requestCancel(MEMBER_ID, ORDER_ID, ITEM_ID, "사유", null))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_CLAIM_NOT_ALLOWED);
		}

		@Test
		@DisplayName("타인의 주문이면 ORDER_NOT_FOUND 예외를 던진다")
		void throwsWhenNotOwner() {
			// given
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			// when & then
			assertThatThrownBy(() -> writer.requestCancel(999L, ORDER_ID, ITEM_ID, "사유", null))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_NOT_FOUND);
		}
	}

	@Nested
	@DisplayName("requestReturn()")
	class RequestReturn {

		@Test
		@DisplayName("배송완료 7일 이내면 반품 클레임을 만든다")
		void createsReturnClaimWithinPeriod() {
			// given
			ReflectionTestUtils.setField(item, "status", OrderItemStatus.DELIVERED);
			ReflectionTestUtils.setField(item, "deliveredAt", NOW.minusDays(7));
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderRepository.findWithItemsByIdAndMemberId(ORDER_ID, MEMBER_ID)).willReturn(Optional.of(order));
			stubSave();

			// when
			OrderClaimRequestResult result = writer.requestReturn(MEMBER_ID, ORDER_ID, ITEM_ID, "사이즈가 안 맞음");

			// then
			assertThat(result.immediate()).isFalse();
			assertThat(item.getClaimStatus()).isEqualTo(OrderItemClaimStatus.RETURN_REQUEST);
		}

		@Test
		@DisplayName("배송완료 7일이 지났으면 ORDER_RETURN_PERIOD_EXPIRED 예외를 던진다")
		void throwsWhenPeriodExpired() {
			// given
			ReflectionTestUtils.setField(item, "status", OrderItemStatus.DELIVERED);
			ReflectionTestUtils.setField(item, "deliveredAt", NOW.minusDays(7).minusSeconds(1));
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderRepository.findWithItemsByIdAndMemberId(ORDER_ID, MEMBER_ID)).willReturn(Optional.of(order));

			// when & then
			assertThatThrownBy(() -> writer.requestReturn(MEMBER_ID, ORDER_ID, ITEM_ID, "사유"))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_RETURN_PERIOD_EXPIRED);
		}

		@Test
		@DisplayName("DELIVERED 가 아니면 ORDER_CLAIM_NOT_ALLOWED 예외를 던진다")
		void throwsWhenNotDelivered() {
			// given
			ReflectionTestUtils.setField(item, "status", OrderItemStatus.PAID);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderRepository.findWithItemsByIdAndMemberId(ORDER_ID, MEMBER_ID)).willReturn(Optional.of(order));

			// when & then
			assertThatThrownBy(() -> writer.requestReturn(MEMBER_ID, ORDER_ID, ITEM_ID, "사유"))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_CLAIM_NOT_ALLOWED);
		}
	}

	@Nested
	@DisplayName("withdraw()")
	class Withdraw {

		@Test
		@DisplayName("REQUESTED 클레임을 철회하면 상품주문의 클레임 표시를 지운다")
		void clearsClaimStatus() {
			// given
			ReflectionTestUtils.setField(item, "claimStatus", OrderItemClaimStatus.CANCEL_REQUEST);
			OrderClaim claim = OrderClaim.requestCancel(item, "사유", null, NOW.minusMinutes(5));
			ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
			given(orderClaimRepository.findOrderIdByIdAndMemberId(CLAIM_ID, MEMBER_ID))
					.willReturn(Optional.of(ORDER_ID));
			given(orderClaimRepository.findWithOrderItemById(CLAIM_ID)).willReturn(Optional.of(claim));
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			// when
			Long itemId = writer.withdraw(MEMBER_ID, CLAIM_ID);

			// then
			assertThat(itemId).isEqualTo(ITEM_ID);
			assertThat(item.getClaimStatus()).isNull();
			assertThat(claim.getStatus()).isEqualTo(OrderClaimStatus.WITHDRAWN);
		}

		@Test
		@DisplayName("주문 락을 잡은 뒤에 클레임을 읽는다")
		void locksOrderBeforeReadingClaim() {
			// given
			ReflectionTestUtils.setField(item, "claimStatus", OrderItemClaimStatus.CANCEL_REQUEST);
			OrderClaim claim = OrderClaim.requestCancel(item, "사유", null, NOW.minusMinutes(5));
			ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
			given(orderClaimRepository.findOrderIdByIdAndMemberId(CLAIM_ID, MEMBER_ID))
					.willReturn(Optional.of(ORDER_ID));
			given(orderClaimRepository.findWithOrderItemById(CLAIM_ID)).willReturn(Optional.of(claim));
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			// when
			writer.withdraw(MEMBER_ID, CLAIM_ID);

			// then
			InOrder inOrder = inOrder(orderRepository, orderClaimRepository);
			inOrder.verify(orderRepository).findByIdForUpdate(ORDER_ID);
			inOrder.verify(orderClaimRepository).findWithOrderItemById(CLAIM_ID);
		}

		@Test
		@DisplayName("환불이 결과를 기다리는 중이면 ORDER_CLAIM_REFUND_IN_PROGRESS 예외를 던지고 클레임을 유지한다")
		void throwsWhenRefundPending() {
			// given
			ReflectionTestUtils.setField(item, "claimStatus", OrderItemClaimStatus.CANCEL_REQUEST);
			OrderClaim claim = OrderClaim.requestCancel(item, "사유", null, NOW.minusMinutes(5));
			ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
			given(orderClaimRepository.findOrderIdByIdAndMemberId(CLAIM_ID, MEMBER_ID))
					.willReturn(Optional.of(ORDER_ID));
			given(orderClaimRepository.findWithOrderItemById(CLAIM_ID)).willReturn(Optional.of(claim));
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(refundReader.hasPendingRefund(CLAIM_ID)).willReturn(true);

			// when & then
			assertThatThrownBy(() -> writer.withdraw(MEMBER_ID, CLAIM_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_CLAIM_REFUND_IN_PROGRESS);
			assertThat(claim.getStatus()).isEqualTo(OrderClaimStatus.REQUESTED);
			assertThat(item.getClaimStatus()).isEqualTo(OrderItemClaimStatus.CANCEL_REQUEST);
		}

		@Test
		@DisplayName("본인 클레임이 아니면 COMMON_RESOURCE_NOT_FOUND 예외를 던진다")
		void throwsWhenNotOwner() {
			// given
			given(orderClaimRepository.findOrderIdByIdAndMemberId(CLAIM_ID, MEMBER_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> writer.withdraw(MEMBER_ID, CLAIM_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_RESOURCE_NOT_FOUND);
		}
	}

	@Nested
	@DisplayName("reject()")
	class Reject {

		@Test
		@DisplayName("클레임을 거부하면 상품주문에 거부 표시를 남긴다")
		void rejectsClaim() {
			// given
			ReflectionTestUtils.setField(item, "status", OrderItemStatus.PREPARING);
			ReflectionTestUtils.setField(item, "claimStatus", OrderItemClaimStatus.CANCEL_REQUEST);
			OrderClaim claim = OrderClaim.requestCancel(item, "사유", null, NOW.minusMinutes(5));
			ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
			stubLockedClaim(claim);

			// when
			OrderClaim rejected = writer.reject(CLAIM_ID, "이미 발송 완료");

			// then
			assertThat(rejected.getStatus()).isEqualTo(OrderClaimStatus.REJECTED);
			assertThat(item.getClaimStatus()).isEqualTo(OrderItemClaimStatus.CANCEL_REJECT);
			assertThat(item.getStatus()).isEqualTo(OrderItemStatus.PREPARING);
		}

		@Test
		@DisplayName("반품 클레임을 거부하면 RETURN_REJECT 표시를 남긴다")
		void marksReturnRejectForReturnClaim() {
			// given
			ReflectionTestUtils.setField(item, "status", OrderItemStatus.DELIVERED);
			ReflectionTestUtils.setField(item, "claimStatus", OrderItemClaimStatus.RETURN_REQUEST);
			OrderClaim claim = OrderClaim.requestReturn(item, "사유", NOW.minusMinutes(5));
			ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
			stubLockedClaim(claim);

			// when
			writer.reject(CLAIM_ID, "상태 불량");

			// then
			assertThat(item.getClaimStatus()).isEqualTo(OrderItemClaimStatus.RETURN_REJECT);
		}

		@Test
		@DisplayName("환불이 결과를 기다리는 중이면 ORDER_CLAIM_REFUND_IN_PROGRESS 예외를 던지고 클레임을 유지한다")
		void throwsWhenRefundPending() {
			// given
			ReflectionTestUtils.setField(item, "status", OrderItemStatus.PREPARING);
			ReflectionTestUtils.setField(item, "claimStatus", OrderItemClaimStatus.CANCEL_REQUEST);
			OrderClaim claim = OrderClaim.requestCancel(item, "사유", null, NOW.minusMinutes(5));
			ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
			stubLockedClaim(claim);
			given(refundReader.hasPendingRefund(CLAIM_ID)).willReturn(true);

			// when & then
			assertThatThrownBy(() -> writer.reject(CLAIM_ID, "사유"))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_CLAIM_REFUND_IN_PROGRESS);
			assertThat(claim.getStatus()).isEqualTo(OrderClaimStatus.REQUESTED);
		}

		@Test
		@DisplayName("클레임이 없으면 COMMON_RESOURCE_NOT_FOUND 예외를 던진다")
		void throwsWhenClaimMissing() {
			// given
			given(orderClaimRepository.findOrderIdById(CLAIM_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> writer.reject(CLAIM_ID, "사유"))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_RESOURCE_NOT_FOUND);
		}
	}

	@Nested
	@DisplayName("startCollecting()")
	class StartCollecting {

		@Test
		@DisplayName("반품 수거를 시작하면 클레임과 상품주문 모두 COLLECTING 으로 표시한다")
		void startsCollecting() {
			// given
			ReflectionTestUtils.setField(item, "status", OrderItemStatus.DELIVERED);
			ReflectionTestUtils.setField(item, "claimStatus", OrderItemClaimStatus.RETURN_REQUEST);
			OrderClaim claim = OrderClaim.requestReturn(item, "사유", NOW.minusMinutes(5));
			ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
			given(orderClaimRepository.findWithOrderItemById(CLAIM_ID)).willReturn(Optional.of(claim));
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			// when
			OrderClaim collecting = writer.startCollecting(CLAIM_ID);

			// then
			assertThat(collecting.getStatus()).isEqualTo(OrderClaimStatus.COLLECTING);
			assertThat(item.getClaimStatus()).isEqualTo(OrderItemClaimStatus.COLLECTING);
		}
	}

	@Nested
	@DisplayName("requestAdminCancel()")
	class RequestAdminCancel {

		@Test
		@DisplayName("PREPARING 상품주문도 관리자 판매취소는 즉시 대상이다")
		void marksImmediateForPreparingItem() {
			// given
			ReflectionTestUtils.setField(item, "status", OrderItemStatus.PREPARING);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderRepository.findWithItemsById(ORDER_ID)).willReturn(Optional.of(order));
			stubSave();

			// when
			OrderClaimRequestResult result = writer.requestAdminCancel(ORDER_ID, ITEM_ID, "재고 확인 불가");

			// then
			assertThat(result.immediate()).isTrue();
		}
	}

	@Nested
	@DisplayName("chooseRestock()")
	class ChooseRestock {

		@Test
		@DisplayName("COLLECTING 인 반품 클레임에 재입고 여부를 기록한다")
		void recordsRestockChoice() {
			// given
			OrderClaim claim = OrderClaim.requestReturn(item, "사유", NOW.minusMinutes(10));
			ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
			ReflectionTestUtils.setField(claim, "status", OrderClaimStatus.COLLECTING);
			stubLockedClaim(claim);

			// when
			OrderClaim result = writer.chooseRestock(CLAIM_ID, true);

			// then
			assertThat(result.getRestock()).isTrue();
		}

		@Test
		@DisplayName("환불이 결과를 기다리는 중이면 ORDER_CLAIM_REFUND_IN_PROGRESS 예외를 던지고 선택을 덮어쓰지 않는다")
		void throwsWhenRefundPending() {
			// given
			OrderClaim claim = OrderClaim.requestReturn(item, "사유", NOW.minusMinutes(10));
			ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
			ReflectionTestUtils.setField(claim, "status", OrderClaimStatus.COLLECTING);
			stubLockedClaim(claim);
			given(refundReader.hasPendingRefund(CLAIM_ID)).willReturn(true);

			// when & then
			assertThatThrownBy(() -> writer.chooseRestock(CLAIM_ID, false))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_CLAIM_REFUND_IN_PROGRESS);
			assertThat(claim.getRestock()).isNull();
		}
	}

	@Nested
	@DisplayName("lockApprovable()")
	class LockApprovable {

		@Test
		@DisplayName("REQUESTED 상태의 CANCEL 클레임이면 주문 락을 잡은 뒤 클레임을 반환한다")
		void returnsClaimAfterLockingOrder() {
			// given
			OrderClaim claim = OrderClaim.requestCancel(item, "사유", null, NOW.minusMinutes(5));
			ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
			stubLockedClaim(claim);

			// when
			OrderClaim result = writer.lockApprovable(CLAIM_ID);

			// then
			assertThat(result).isSameAs(claim);
			InOrder inOrder = inOrder(orderRepository, orderClaimRepository);
			inOrder.verify(orderRepository).findByIdForUpdate(ORDER_ID);
			inOrder.verify(orderClaimRepository).findWithOrderItemById(CLAIM_ID);
		}

		@Test
		@DisplayName("RETURN 클레임이면 ORDER_CLAIM_NOT_ALLOWED 예외를 던진다")
		void throwsWhenReturnClaim() {
			// given
			OrderClaim claim = OrderClaim.requestReturn(item, "사유", NOW.minusMinutes(5));
			ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
			stubLockedClaim(claim);

			// when & then
			assertThatThrownBy(() -> writer.lockApprovable(CLAIM_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_CLAIM_NOT_ALLOWED);
		}

		@Test
		@DisplayName("REQUESTED 가 아니면 ORDER_CLAIM_NOT_ALLOWED 예외를 던진다")
		void throwsWhenNotRequested() {
			// given
			OrderClaim claim = OrderClaim.requestCancel(item, "사유", null, NOW.minusMinutes(5));
			ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
			ReflectionTestUtils.setField(claim, "status", OrderClaimStatus.REJECTED);
			stubLockedClaim(claim);

			// when & then
			assertThatThrownBy(() -> writer.lockApprovable(CLAIM_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_CLAIM_NOT_ALLOWED);
		}

		@Test
		@DisplayName("환불이 결과를 기다리는 중이면 ORDER_CLAIM_REFUND_IN_PROGRESS 예외를 던진다")
		void throwsWhenRefundPending() {
			// given
			OrderClaim claim = OrderClaim.requestCancel(item, "사유", null, NOW.minusMinutes(5));
			ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
			stubLockedClaim(claim);
			given(refundReader.hasPendingRefund(CLAIM_ID)).willReturn(true);

			// when & then
			assertThatThrownBy(() -> writer.lockApprovable(CLAIM_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_CLAIM_REFUND_IN_PROGRESS);
		}
	}

	@Nested
	@DisplayName("discardUnstartedClaim()")
	class DiscardUnstartedClaim {

		@Test
		@DisplayName("환불 행이 없는 REQUESTED 클레임이면 클레임을 지우고 상품주문 표시를 되돌린다")
		void deletesClaimWhenNoRefundRow() {
			// given
			ReflectionTestUtils.setField(item, "claimStatus", OrderItemClaimStatus.CANCEL_REQUEST);
			OrderClaim claim = OrderClaim.requestCancel(item, "사유", null, NOW.minusMinutes(5));
			ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
			stubLockedClaim(claim);
			given(refundReader.hasAnyRefund(CLAIM_ID)).willReturn(false);

			// when
			writer.discardUnstartedClaim(CLAIM_ID);

			// then
			verify(orderClaimRepository).delete(claim);
			assertThat(item.getClaimStatus()).isNull();
		}

		@Test
		@DisplayName("이 클레임으로 나간 환불 행이 있으면 아무 것도 하지 않는다")
		void skipsWhenRefundRowExists() {
			// given
			ReflectionTestUtils.setField(item, "claimStatus", OrderItemClaimStatus.CANCEL_REQUEST);
			OrderClaim claim = OrderClaim.requestCancel(item, "사유", null, NOW.minusMinutes(5));
			ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
			stubLockedClaim(claim);
			given(refundReader.hasAnyRefund(CLAIM_ID)).willReturn(true);

			// when
			writer.discardUnstartedClaim(CLAIM_ID);

			// then
			verify(orderClaimRepository, never()).delete(any(OrderClaim.class));
			assertThat(item.getClaimStatus()).isEqualTo(OrderItemClaimStatus.CANCEL_REQUEST);
		}

		@Test
		@DisplayName("이미 REQUESTED 가 아닌 클레임이면 아무 것도 하지 않는다")
		void skipsWhenNotRequested() {
			// given
			ReflectionTestUtils.setField(item, "claimStatus", OrderItemClaimStatus.CANCEL_REJECT);
			OrderClaim claim = OrderClaim.requestCancel(item, "사유", null, NOW.minusMinutes(5));
			ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
			ReflectionTestUtils.setField(claim, "status", OrderClaimStatus.REJECTED);
			stubLockedClaim(claim);

			// when
			writer.discardUnstartedClaim(CLAIM_ID);

			// then
			verify(orderClaimRepository, never()).delete(any(OrderClaim.class));
			assertThat(item.getClaimStatus()).isEqualTo(OrderItemClaimStatus.CANCEL_REJECT);
		}
	}
}
