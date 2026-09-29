package com.groove.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
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
import com.groove.global.common.PageResponse;
import com.groove.member.entity.Member;
import com.groove.order.dto.AdminOrderItemBulkResultResponse;
import com.groove.order.dto.AdminOrderItemConfirmRequest;
import com.groove.order.dto.AdminOrderItemDeliverRequest;
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
	private AdminAuditLogService adminAuditLogService;

	private AdminOrderItemService service;

	private Member member;
	private Product product;
	private Clock clock;

	@BeforeEach
	void setUp() {
		clock = Clock.fixed(Instant.parse("2026-09-20T03:00:00Z"), ZoneId.of("Asia/Seoul"));
		service = new AdminOrderItemService(orderQueryMapper, orderItemRepository, orderRepository,
				adminAuditLogService, clock);
		member = MemberFixture.withId(MemberFixture.create(), 1L);
		Artist artist = ArtistFixture.withId(1L);
		product = ProductFixture.withId(ProductFixture.create(artist), 100L);
	}

	@Nested
	@DisplayName("getList()")
	class GetList {

		@Test
		@DisplayName("조건에 맞는 상품주문이 없으면 빈 페이지를 반환한다")
		void returnsEmptyPageWhenNoItems() {
			// given
			given(orderQueryMapper.countAdminOrderItems(any())).willReturn(0L);
			AdminOrderItemSearchRequest request = new AdminOrderItemSearchRequest(null, null, null, null, null, null);

			// when
			PageResponse<AdminOrderItemSummaryResponse> response = service.getList(request);

			// then
			assertThat(response.content()).isEmpty();
			verify(orderQueryMapper, never()).findAdminOrderItems(any());
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
			given(orderRepository.findByIdForUpdate(500L)).willReturn(Optional.of(order));
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
			given(orderRepository.findByIdForUpdate(500L)).willReturn(Optional.of(order));
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
			given(orderRepository.findByIdForUpdate(100L)).willReturn(Optional.of(order2));
			given(orderRepository.findByIdForUpdate(200L)).willReturn(Optional.of(order1));
			given(orderItemRepository.findAllById(List.of(700L, 800L)))
					.willReturn(List.of(order1.getItems().get(0), order2.getItems().get(0)));

			// when
			service.confirmPreparing(ADMIN_ID, new AdminOrderItemConfirmRequest(List.of(700L, 800L)));

			// then
			InOrder inOrder = Mockito.inOrder(orderRepository);
			inOrder.verify(orderRepository).findByIdForUpdate(100L);
			inOrder.verify(orderRepository).findByIdForUpdate(200L);
		}

		@Test
		@DisplayName("상품주문이 속한 주문을 찾지 못하면 ORDER_NOT_FOUND 예외를 던진다")
		void throwsWhenOrderNotFound() {
			// given
			given(orderItemRepository.findDistinctOrderIdsByIdIn(List.of(900L))).willReturn(List.of(500L));
			given(orderRepository.findByIdForUpdate(500L)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> service.confirmPreparing(ADMIN_ID,
					new AdminOrderItemConfirmRequest(List.of(900L))))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_NOT_FOUND);
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
			given(orderRepository.findByIdForUpdate(500L)).willReturn(Optional.of(order));
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
			given(orderRepository.findByIdForUpdate(100L)).willReturn(Optional.of(order1));
			given(orderRepository.findByIdForUpdate(200L)).willReturn(Optional.of(order2));
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
			given(orderRepository.findByIdForUpdate(500L)).willReturn(Optional.of(order));
			given(orderItemRepository.findAllById(List.of(900L))).willReturn(List.of(item));

			// when
			AdminOrderItemBulkResultResponse result = service.startShipping(ADMIN_ID,
					shipRequest(shipItem(900L, "CJ", "123456789012")));

			// then
			assertThat(result.processed()).isZero();
			assertThat(result.skipped()).isEqualTo(1);
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
			given(orderRepository.findByIdForUpdate(500L)).willReturn(Optional.of(order));
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
			given(orderRepository.findByIdForUpdate(500L)).willReturn(Optional.of(order));
			given(orderItemRepository.findAllById(List.of(900L))).willReturn(List.of(item));

			// when
			AdminOrderItemBulkResultResponse result = service.completeDelivery(ADMIN_ID,
					new AdminOrderItemDeliverRequest(List.of(900L)));

			// then
			assertThat(result.processed()).isZero();
			assertThat(result.skipped()).isEqualTo(1);
		}
	}

	private Order orderWithItem(Long orderId, Long itemId, OrderItemStatus status) {
		Order order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 1), orderId);
		OrderFixture.markItemsStatus(order, status);
		ReflectionTestUtils.setField(order.getItems().get(0), "id", itemId);
		return order;
	}
}
