package com.groove.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditTargetType;
import com.groove.admin.service.AdminAuditLogService;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.SliceResponse;
import com.groove.member.entity.Member;
import com.groove.order.dto.AdminOrderItemBulkResultResponse;
import com.groove.order.dto.AdminOrderItemConfirmRequest;
import com.groove.order.dto.AdminOrderItemCountResponse;
import com.groove.order.dto.AdminOrderItemDeliverRequest;
import com.groove.order.dto.AdminOrderItemSearchCondition;
import com.groove.order.dto.AdminOrderItemSearchRequest;
import com.groove.order.dto.AdminOrderItemShipRequest;
import com.groove.order.dto.AdminOrderItemSummaryResponse;
import com.groove.order.entity.CourierCode;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderItem;
import com.groove.order.entity.OrderItemClaimStatus;
import com.groove.order.entity.OrderItemStatus;
import com.groove.order.mapper.OrderQueryMapper;
import com.groove.order.repository.OrderItemRepository;
import com.groove.order.repository.OrderRepository;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;

@ExtendWith(MockitoExtension.class)
class AdminOrderItemServiceTest {

	private static final Long ADMIN_ID = 1L;

	@Mock
	private OrderQueryMapper orderQueryMapper;

	@Mock
	private OrderItemRepository orderItemRepository;

	@Mock
	private OrderRepository orderRepository;

	@Mock
	private PaymentRepository paymentRepository;

	@Mock
	private AdminAuditLogService adminAuditLogService;

	private AdminOrderItemService service;

	private Member member;
	private Product product;
	private Clock clock;

	@BeforeEach
	void setUp() {
		clock = Clock.fixed(Instant.parse("2026-09-20T03:00:00Z"), ZoneId.of("Asia/Seoul"));
		service = new AdminOrderItemService(orderQueryMapper, orderItemRepository, orderRepository,
				paymentRepository, adminAuditLogService, clock);
		member = MemberFixture.withId(MemberFixture.create(), 1L);
		Artist artist = ArtistFixture.withId(1L);
		product = ProductFixture.withId(ProductFixture.create(artist), 100L);
	}

	@Nested
	@DisplayName("getList()")
	class GetList {

		@Test
		@DisplayName("건수 쿼리를 호출하지 않고 size + 1 개를 조회한다")
		void doesNotCountAndFetchesOneExtra() {
			// given
			given(orderQueryMapper.findAdminOrderItems(any())).willReturn(List.of());
			AdminOrderItemSearchRequest request = new AdminOrderItemSearchRequest(null, null, null, null, 2, 10);

			// when
			service.getList(request);

			// then
			verify(orderQueryMapper, never()).countAdminOrderItems(any());
			ArgumentCaptor<AdminOrderItemSearchCondition> captor = ArgumentCaptor
					.forClass(AdminOrderItemSearchCondition.class);
			verify(orderQueryMapper).findAdminOrderItems(captor.capture());
			assertThat(captor.getValue().fetchSize()).isEqualTo(11);
			assertThat(captor.getValue().offset()).isEqualTo(20);
		}

		@Test
		@DisplayName("size + 1 개가 조회되면 size 개만 담고 hasNext 가 true 다")
		void flagsHasNextWhenExtraFetched() {
			// given
			given(orderQueryMapper.findAdminOrderItems(any())).willReturn(summaries(3));
			AdminOrderItemSearchRequest request = new AdminOrderItemSearchRequest(null, null, null, null, 0, 2);

			// when
			SliceResponse<AdminOrderItemSummaryResponse> response = service.getList(request);

			// then
			assertThat(response.content()).hasSize(2);
			assertThat(response.hasNext()).isTrue();
		}

		@Test
		@DisplayName("size 이하가 조회되면 전부 담고 hasNext 가 false 다")
		void noNextWhenWithinSize() {
			// given
			given(orderQueryMapper.findAdminOrderItems(any())).willReturn(summaries(2));
			AdminOrderItemSearchRequest request = new AdminOrderItemSearchRequest(null, null, null, null, 0, 2);

			// when
			SliceResponse<AdminOrderItemSummaryResponse> response = service.getList(request);

			// then
			assertThat(response.content()).hasSize(2);
			assertThat(response.hasNext()).isFalse();
		}

		@Test
		@DisplayName("조건에 맞는 상품주문이 없으면 빈 목록을 반환한다")
		void returnsEmptyWhenNoItems() {
			// given
			given(orderQueryMapper.findAdminOrderItems(any())).willReturn(List.of());
			AdminOrderItemSearchRequest request = new AdminOrderItemSearchRequest(null, null, null, null, null, null);

			// when
			SliceResponse<AdminOrderItemSummaryResponse> response = service.getList(request);

			// then
			assertThat(response.content()).isEmpty();
			assertThat(response.hasNext()).isFalse();
		}

		private List<AdminOrderItemSummaryResponse> summaries(int count) {
			return IntStream.range(0, count)
					.mapToObj(i -> new AdminOrderItemSummaryResponse(900L + i, 700L, "20260903-TESTAB12-0" + i,
							"20260903-TESTAB12", "buyer@groove.com", "그루브 앨범", 1, OrderItemStatus.PAID, null, null,
							null, LocalDateTime.now(), false))
					.toList();
		}
	}

	@Nested
	@DisplayName("count()")
	class Count {

		@Test
		@DisplayName("매퍼가 센 건수를 그대로 반환한다")
		void returnsMapperCount() {
			// given
			given(orderQueryMapper.countAdminOrderItems(any())).willReturn(42L);
			AdminOrderItemSearchRequest request = new AdminOrderItemSearchRequest(null, null, null, null, null, null);

			// when
			AdminOrderItemCountResponse response = service.count(request);

			// then
			assertThat(response.totalElements()).isEqualTo(42L);
		}
	}

	@Nested
	@DisplayName("confirmPreparing()")
	class ConfirmPreparing {

		@Test
		@DisplayName("PAID 상품주문을 PREPARING 으로 바꾸고 감사 로그를 남긴다")
		void confirmsAndRecordsAudit() {
			// given
			Order order = orderWithItem(500L, 900L, OrderItemStatus.PAID);
			OrderItem item = order.getItems().get(0);
			given(orderItemRepository.findDistinctOrderIdsByIdIn(List.of(900L))).willReturn(List.of(500L));
			given(orderRepository.findAllByIdInForUpdate(List.of(500L))).willReturn(List.of(order));
			given(orderItemRepository.findAllById(List.of(900L))).willReturn(List.of(item));

			// when
			AdminOrderItemBulkResultResponse result = service.confirmPreparing(ADMIN_ID,
					new AdminOrderItemConfirmRequest(List.of(900L)));

			// then
			assertThat(result.processed()).isEqualTo(1);
			assertThat(result.skipped()).isZero();
			assertThat(item.getStatus()).isEqualTo(OrderItemStatus.PREPARING);
			verify(adminAuditLogService).record(eq(ADMIN_ID), eq(AdminAuditAction.ORDER_STATUS_CHANGE),
					eq(AdminAuditTargetType.ORDER), eq(500L), eq("PAID->PREPARING"));
		}

		@Test
		@DisplayName("PAID 가 아닌 항목은 건너뛰고 감사 로그도 남기지 않는다")
		void skipsNonPaidItemsWithoutAudit() {
			// given
			Order order = orderWithItem(500L, 900L, OrderItemStatus.PREPARING);
			OrderItem item = order.getItems().get(0);
			given(orderItemRepository.findDistinctOrderIdsByIdIn(List.of(900L))).willReturn(List.of(500L));
			given(orderRepository.findAllByIdInForUpdate(List.of(500L))).willReturn(List.of(order));
			given(orderItemRepository.findAllById(List.of(900L))).willReturn(List.of(item));

			// when
			AdminOrderItemBulkResultResponse result = service.confirmPreparing(ADMIN_ID,
					new AdminOrderItemConfirmRequest(List.of(900L)));

			// then
			assertThat(result.processed()).isZero();
			assertThat(result.skipped()).isEqualTo(1);
			verify(adminAuditLogService, never()).record(any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("대상 상품주문이 속한 주문 id 를 오름차순으로 잠근다")
		void locksOrdersInAscendingOrder() {
			// given
			Order order1 = orderWithItem(200L, 700L, OrderItemStatus.PAID);
			Order order2 = orderWithItem(100L, 800L, OrderItemStatus.PAID);
			given(orderItemRepository.findDistinctOrderIdsByIdIn(List.of(700L, 800L)))
					.willReturn(List.of(200L, 100L));
			given(orderRepository.findAllByIdInForUpdate(List.of(100L, 200L))).willReturn(List.of(order2, order1));
			given(orderItemRepository.findAllById(List.of(700L, 800L)))
					.willReturn(List.of(order1.getItems().get(0), order2.getItems().get(0)));

			// when
			service.confirmPreparing(ADMIN_ID, new AdminOrderItemConfirmRequest(List.of(700L, 800L)));

			// then
			verify(orderRepository, times(1)).findAllByIdInForUpdate(List.of(100L, 200L));
			verify(orderRepository, never()).findByIdForUpdate(any());
		}

		@Test
		@DisplayName("진행 중인 클레임이 있는 항목은 건너뛴다")
		void skipsItemWithInProgressClaim() {
			// given
			Order order = orderWithItem(500L, 900L, OrderItemStatus.PAID);
			OrderItem item = order.getItems().get(0);
			ReflectionTestUtils.setField(item, "claimStatus", OrderItemClaimStatus.CANCEL_REQUEST);
			given(orderItemRepository.findDistinctOrderIdsByIdIn(List.of(900L))).willReturn(List.of(500L));
			given(orderRepository.findAllByIdInForUpdate(List.of(500L))).willReturn(List.of(order));
			given(orderItemRepository.findAllById(List.of(900L))).willReturn(List.of(item));

			// when
			AdminOrderItemBulkResultResponse result = service.confirmPreparing(ADMIN_ID,
					new AdminOrderItemConfirmRequest(List.of(900L)));

			// then
			assertThat(result.processed()).isZero();
			assertThat(result.skipped()).isEqualTo(1);
			assertThat(item.getStatus()).isEqualTo(OrderItemStatus.PAID);
		}

		@Test
		@DisplayName("전액취소가 진행 중인 주문의 항목은 건너뛰고 감사 로그도 남기지 않는다")
		void skipsItemsOfCancelRequestedOrder() {
			// given
			Order order = orderWithItem(500L, 900L, OrderItemStatus.PAID);
			OrderItem item = order.getItems().get(0);
			given(orderItemRepository.findDistinctOrderIdsByIdIn(List.of(900L))).willReturn(List.of(500L));
			given(orderRepository.findAllByIdInForUpdate(List.of(500L))).willReturn(List.of(order));
			given(paymentRepository.findOrderIdsByOrderIdInAndStatus(List.of(500L), PaymentStatus.CANCEL_REQUESTED))
					.willReturn(List.of(500L));
			given(orderItemRepository.findAllById(List.of(900L))).willReturn(List.of(item));

			// when
			AdminOrderItemBulkResultResponse result = service.confirmPreparing(ADMIN_ID,
					new AdminOrderItemConfirmRequest(List.of(900L)));

			// then
			assertThat(result.processed()).isZero();
			assertThat(result.skipped()).isEqualTo(1);
			assertThat(item.getStatus()).isEqualTo(OrderItemStatus.PAID);
			verify(adminAuditLogService, never()).record(any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("상품주문이 속한 주문을 찾지 못하면 ORDER_NOT_FOUND 예외를 던진다")
		void throwsWhenOrderNotFound() {
			// given
			given(orderItemRepository.findDistinctOrderIdsByIdIn(List.of(900L))).willReturn(List.of(500L));
			given(orderRepository.findAllByIdInForUpdate(List.of(500L))).willReturn(List.of());

			// when & then
			assertThatThrownBy(() -> service.confirmPreparing(ADMIN_ID,
					new AdminOrderItemConfirmRequest(List.of(900L))))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_NOT_FOUND);
		}

		@Test
		@DisplayName("잠근 주문 수가 대상 주문 수보다 적으면 ORDER_NOT_FOUND 예외를 던지고 항목을 조회하지 않는다")
		void throwsWhenLockedOrderCountIsShort() {
			// given
			Order order = orderWithItem(100L, 700L, OrderItemStatus.PAID);
			given(orderItemRepository.findDistinctOrderIdsByIdIn(List.of(700L, 800L)))
					.willReturn(List.of(200L, 100L));
			given(orderRepository.findAllByIdInForUpdate(List.of(100L, 200L))).willReturn(List.of(order));

			// when & then
			assertThatThrownBy(() -> service.confirmPreparing(ADMIN_ID,
					new AdminOrderItemConfirmRequest(List.of(700L, 800L))))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_NOT_FOUND);
			verify(orderItemRepository, never()).findAllById(any());
		}

		@Test
		@DisplayName("대상 상품주문이 하나도 없으면 주문을 잠그지 않고 건너뛴다")
		void doesNotLockWhenNoOrderFound() {
			// given
			given(orderItemRepository.findDistinctOrderIdsByIdIn(List.of(900L))).willReturn(List.of());

			// when
			AdminOrderItemBulkResultResponse result = service.confirmPreparing(ADMIN_ID,
					new AdminOrderItemConfirmRequest(List.of(900L)));

			// then
			assertThat(result.processed()).isZero();
			verify(orderRepository, never()).findAllByIdInForUpdate(any());
		}

		@Test
		@DisplayName("같은 id 를 두 번 보내면 한 번으로 세어 skipped 가 0 이다")
		void countsDuplicateIdsOnce() {
			// given
			Order order = orderWithItem(500L, 900L, OrderItemStatus.PAID);
			OrderItem item = order.getItems().get(0);
			given(orderItemRepository.findDistinctOrderIdsByIdIn(List.of(900L))).willReturn(List.of(500L));
			given(orderRepository.findAllByIdInForUpdate(List.of(500L))).willReturn(List.of(order));
			given(orderItemRepository.findAllById(List.of(900L))).willReturn(List.of(item));

			// when
			AdminOrderItemBulkResultResponse result = service.confirmPreparing(ADMIN_ID,
					new AdminOrderItemConfirmRequest(List.of(900L, 900L)));

			// then
			assertThat(result.processed()).isEqualTo(1);
			assertThat(result.skipped()).isZero();
		}
	}

	@Nested
	@DisplayName("startShipping()")
	class StartShipping {

		@Test
		@DisplayName("PAID·PREPARING 상품주문을 SHIPPING 으로 바꾸고 택배사·송장을 기록한다")
		void startsShipping() {
			// given
			Order order = orderWithItem(500L, 900L, OrderItemStatus.PREPARING);
			OrderItem item = order.getItems().get(0);
			given(orderItemRepository.findDistinctOrderIdsByIdIn(List.of(900L))).willReturn(List.of(500L));
			given(orderRepository.findAllByIdInForUpdate(List.of(500L))).willReturn(List.of(order));
			given(orderItemRepository.findAllById(List.of(900L))).willReturn(List.of(item));

			// when
			AdminOrderItemBulkResultResponse result = service.startShipping(ADMIN_ID,
					shipRequest(shipItem(900L, "CJ", "123456789012")));

			// then
			assertThat(result.processed()).isEqualTo(1);
			assertThat(item.getStatus()).isEqualTo(OrderItemStatus.SHIPPING);
			assertThat(item.getCourierCode()).isEqualTo(CourierCode.CJ);
			assertThat(item.getTrackingNumber()).isEqualTo("123456789012");
		}

		@Test
		@DisplayName("서로 다른 주문의 상품주문마다 다른 택배사·송장을 각각 기록한다")
		void startsShippingWithPerItemCourierAndTracking() {
			// given
			Order order1 = orderWithItem(100L, 700L, OrderItemStatus.PAID);
			Order order2 = orderWithItem(200L, 800L, OrderItemStatus.PREPARING);
			OrderItem item1 = order1.getItems().get(0);
			OrderItem item2 = order2.getItems().get(0);
			given(orderItemRepository.findDistinctOrderIdsByIdIn(List.of(700L, 800L)))
					.willReturn(List.of(100L, 200L));
			given(orderRepository.findAllByIdInForUpdate(List.of(100L, 200L))).willReturn(List.of(order1, order2));
			given(orderItemRepository.findAllById(List.of(700L, 800L))).willReturn(List.of(item1, item2));

			// when
			AdminOrderItemBulkResultResponse result = service.startShipping(ADMIN_ID,
					shipRequest(shipItem(700L, "CJ", "111111111111"), shipItem(800L, "HANJIN", "222222222222")));

			// then
			assertThat(result.processed()).isEqualTo(2);
			assertThat(item1.getCourierCode()).isEqualTo(CourierCode.CJ);
			assertThat(item1.getTrackingNumber()).isEqualTo("111111111111");
			assertThat(item2.getCourierCode()).isEqualTo(CourierCode.HANJIN);
			assertThat(item2.getTrackingNumber()).isEqualTo("222222222222");
		}

		@Test
		@DisplayName("진행 중인 클레임이 있으면 건너뛴다")
		void skipsItemWithInProgressClaim() {
			// given
			Order order = orderWithItem(500L, 900L, OrderItemStatus.PAID);
			OrderItem item = order.getItems().get(0);
			ReflectionTestUtils.setField(item, "claimStatus", OrderItemClaimStatus.CANCEL_REQUEST);
			given(orderItemRepository.findDistinctOrderIdsByIdIn(List.of(900L))).willReturn(List.of(500L));
			given(orderRepository.findAllByIdInForUpdate(List.of(500L))).willReturn(List.of(order));
			given(orderItemRepository.findAllById(List.of(900L))).willReturn(List.of(item));

			// when
			AdminOrderItemBulkResultResponse result = service.startShipping(ADMIN_ID,
					shipRequest(shipItem(900L, "CJ", "123456789012")));

			// then
			assertThat(result.processed()).isZero();
			assertThat(result.skipped()).isEqualTo(1);
		}

		@Test
		@DisplayName("전액취소가 진행 중인 주문의 항목은 건너뛴다")
		void skipsItemsOfCancelRequestedOrder() {
			// given
			Order order = orderWithItem(500L, 900L, OrderItemStatus.PAID);
			OrderItem item = order.getItems().get(0);
			given(orderItemRepository.findDistinctOrderIdsByIdIn(List.of(900L))).willReturn(List.of(500L));
			given(orderRepository.findAllByIdInForUpdate(List.of(500L))).willReturn(List.of(order));
			given(paymentRepository.findOrderIdsByOrderIdInAndStatus(List.of(500L), PaymentStatus.CANCEL_REQUESTED))
					.willReturn(List.of(500L));
			given(orderItemRepository.findAllById(List.of(900L))).willReturn(List.of(item));

			// when
			AdminOrderItemBulkResultResponse result = service.startShipping(ADMIN_ID,
					shipRequest(shipItem(900L, "CJ", "123456789012")));

			// then
			assertThat(result.processed()).isZero();
			assertThat(result.skipped()).isEqualTo(1);
			assertThat(item.getStatus()).isEqualTo(OrderItemStatus.PAID);
			assertThat(item.getTrackingNumber()).isNull();
		}

		@Test
		@DisplayName("존재하지 않는 택배사 코드면 COMMON_VALIDATION_FAILED 예외를 던지고 아무 것도 잠그지 않는다")
		void throwsWhenCourierCodeInvalid() {
			// when & then
			assertThatThrownBy(() -> service.startShipping(ADMIN_ID,
					shipRequest(shipItem(900L, "INVALID", "123456789012"))))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED);
			verify(orderItemRepository, never()).findDistinctOrderIdsByIdIn(any());
		}

		@Test
		@DisplayName("같은 id 를 두 번 보내면 한 번으로 세어 skipped 가 0 이다")
		void countsDuplicateIdsOnce() {
			// given
			Order order = orderWithItem(500L, 900L, OrderItemStatus.PAID);
			OrderItem item = order.getItems().get(0);
			given(orderItemRepository.findDistinctOrderIdsByIdIn(List.of(900L))).willReturn(List.of(500L));
			given(orderRepository.findAllByIdInForUpdate(List.of(500L))).willReturn(List.of(order));
			given(orderItemRepository.findAllById(List.of(900L))).willReturn(List.of(item));

			// when
			AdminOrderItemBulkResultResponse result = service.startShipping(ADMIN_ID,
					shipRequest(shipItem(900L, "CJ", "123456789012"), shipItem(900L, "CJ", "123456789012")));

			// then
			assertThat(result.processed()).isEqualTo(1);
			assertThat(result.skipped()).isZero();
		}

		private AdminOrderItemShipRequest.ShipItem shipItem(Long orderItemId, String courierCode,
				String trackingNumber) {
			return new AdminOrderItemShipRequest.ShipItem(orderItemId, courierCode, trackingNumber);
		}

		private AdminOrderItemShipRequest shipRequest(AdminOrderItemShipRequest.ShipItem... items) {
			return new AdminOrderItemShipRequest(List.of(items));
		}
	}

	@Nested
	@DisplayName("completeDelivery()")
	class CompleteDelivery {

		@Test
		@DisplayName("SHIPPING 상품주문을 DELIVERED 로 바꾼다")
		void completes() {
			// given
			Order order = orderWithItem(500L, 900L, OrderItemStatus.SHIPPING);
			OrderItem item = order.getItems().get(0);
			given(orderItemRepository.findDistinctOrderIdsByIdIn(List.of(900L))).willReturn(List.of(500L));
			given(orderRepository.findAllByIdInForUpdate(List.of(500L))).willReturn(List.of(order));
			given(orderItemRepository.findAllById(List.of(900L))).willReturn(List.of(item));

			// when
			AdminOrderItemBulkResultResponse result = service.completeDelivery(ADMIN_ID,
					new AdminOrderItemDeliverRequest(List.of(900L)));

			// then
			assertThat(result.processed()).isEqualTo(1);
			assertThat(item.getStatus()).isEqualTo(OrderItemStatus.DELIVERED);
		}

		@Test
		@DisplayName("SHIPPING 이 아니면 건너뛴다")
		void skipsWhenNotShipping() {
			// given
			Order order = orderWithItem(500L, 900L, OrderItemStatus.PAID);
			OrderItem item = order.getItems().get(0);
			given(orderItemRepository.findDistinctOrderIdsByIdIn(List.of(900L))).willReturn(List.of(500L));
			given(orderRepository.findAllByIdInForUpdate(List.of(500L))).willReturn(List.of(order));
			given(orderItemRepository.findAllById(List.of(900L))).willReturn(List.of(item));

			// when
			AdminOrderItemBulkResultResponse result = service.completeDelivery(ADMIN_ID,
					new AdminOrderItemDeliverRequest(List.of(900L)));

			// then
			assertThat(result.processed()).isZero();
			assertThat(result.skipped()).isEqualTo(1);
		}

		@Test
		@DisplayName("같은 id 를 두 번 보내면 한 번으로 세어 skipped 가 0 이다")
		void countsDuplicateIdsOnce() {
			// given
			Order order = orderWithItem(500L, 900L, OrderItemStatus.SHIPPING);
			OrderItem item = order.getItems().get(0);
			given(orderItemRepository.findDistinctOrderIdsByIdIn(List.of(900L))).willReturn(List.of(500L));
			given(orderRepository.findAllByIdInForUpdate(List.of(500L))).willReturn(List.of(order));
			given(orderItemRepository.findAllById(List.of(900L))).willReturn(List.of(item));

			// when
			AdminOrderItemBulkResultResponse result = service.completeDelivery(ADMIN_ID,
					new AdminOrderItemDeliverRequest(List.of(900L, 900L)));

			// then
			assertThat(result.processed()).isEqualTo(1);
			assertThat(result.skipped()).isZero();
		}
	}

	private Order orderWithItem(Long orderId, Long itemId, OrderItemStatus status) {
		Order order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 1), orderId);
		OrderFixture.markItemsStatus(order, status);
		ReflectionTestUtils.setField(order.getItems().get(0), "id", itemId);
		return order;
	}
}
