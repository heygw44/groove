package com.groove.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditTargetType;
import com.groove.admin.service.AdminAuditLogService;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.PaymentFixture;
import com.groove.fixture.ProductFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.PageResponse;
import com.groove.member.entity.Member;
import com.groove.order.dto.AdminOrderDetailResponse;
import com.groove.order.dto.AdminOrderSearchRequest;
import com.groove.order.dto.AdminOrderStatusChangeRequest;
import com.groove.order.dto.AdminOrderSummaryResponse;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderStatus;
import com.groove.order.mapper.OrderQueryMapper;
import com.groove.order.repository.OrderRepository;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;

@ExtendWith(MockitoExtension.class)
class AdminOrderServiceTest {

	private static final Long ADMIN_ID = 1L;
	private static final Long ORDER_ID = 500L;

	@Mock
	OrderRepository orderRepository;

	@Mock
	OrderQueryMapper orderQueryMapper;

	@Mock
	AdminAuditLogService adminAuditLogService;

	@Mock
	PaymentRepository paymentRepository;

	AdminOrderService service;
	Member member;
	Product product;

	@BeforeEach
	void setUp() {
		service = new AdminOrderService(orderRepository, orderQueryMapper, adminAuditLogService, paymentRepository);
		member = MemberFixture.withId(MemberFixture.create(), 1L);
		Artist artist = ArtistFixture.withId(1L);
		product = ProductFixture.withId(ProductFixture.create(artist), 100L);
	}

	@Nested
	@DisplayName("getList()")
	class GetList {

		@Test
		@DisplayName("조건에 맞는 주문이 없으면 빈 페이지를 반환한다")
		void returnsEmptyPageWhenNoOrders() {
			// given
			given(orderQueryMapper.countAdminOrders(any())).willReturn(0L);
			AdminOrderSearchRequest request = new AdminOrderSearchRequest(null, null, null, null, null, null);

			// when
			PageResponse<AdminOrderSummaryResponse> response = service.getList(request);

			// then
			assertThat(response.content()).isEmpty();
			verify(orderQueryMapper, never()).findAdminOrders(any());
		}

		@Test
		@DisplayName("조건에 맞는 주문이 있으면 목록을 반환한다")
		void returnsOrdersWhenPresent() {
			// given
			AdminOrderSummaryResponse summary = new AdminOrderSummaryResponse(ORDER_ID, "20260903-TESTAB12",
					"buyer@groove.com", OrderStatus.PAID, new BigDecimal("30000"), 1, null);
			given(orderQueryMapper.countAdminOrders(any())).willReturn(1L);
			given(orderQueryMapper.findAdminOrders(any())).willReturn(List.of(summary));

			// when
			PageResponse<AdminOrderSummaryResponse> response = service.getList(
					new AdminOrderSearchRequest(null, null, null, null, null, null));

			// then
			assertThat(response.content()).containsExactly(summary);
		}
	}

	@Nested
	@DisplayName("getDetail()")
	class GetDetail {

		@Test
		@DisplayName("결제가 있으면 결제 상태를 포함한다")
		void includesPaymentStatus() {
			// given
			Order order = orderWithStatus(OrderStatus.PAID);
			given(orderRepository.findWithItemsAndMemberById(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(PaymentFixture.approved(order)));

			// when
			AdminOrderDetailResponse response = service.getDetail(ORDER_ID);

			// then
			assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.DONE);
		}
	}

	@Nested
	@DisplayName("changeStatus()")
	class ChangeStatus {

		@Test
		@DisplayName("허용된 전이면 상태를 바꾸고 감사 로그를 남긴다")
		void changesStatusAndRecordsAuditLog() {
			// given
			Order order = orderWithStatus(OrderStatus.PAID);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderRepository.findWithItemsAndMemberById(ORDER_ID)).willReturn(Optional.of(order));

			// when
			AdminOrderDetailResponse response = service.changeStatus(ADMIN_ID, ORDER_ID,
					new AdminOrderStatusChangeRequest(OrderStatus.PREPARING));

			// then
			assertThat(response.status()).isEqualTo(OrderStatus.PREPARING);
			verify(adminAuditLogService).record(eq(ADMIN_ID), eq(AdminAuditAction.ORDER_STATUS_CHANGE),
					eq(AdminAuditTargetType.ORDER), eq(ORDER_ID), eq("PAID->PREPARING"));
		}

		@Test
		@DisplayName("CANCELED 전이는 진입 서비스 사용을 강제한다")
		void rejectsCanceledTransition() {
			// when & then
			assertThatThrownBy(() -> service.changeStatus(ADMIN_ID, ORDER_ID,
					new AdminOrderStatusChangeRequest(OrderStatus.CANCELED)))
					.isInstanceOf(IllegalStateException.class);
			verify(orderRepository, never()).findByIdForUpdate(any());
		}

		@Test
		@DisplayName("취소 요청 중이면 다음 배송 상태 전이를 거절한다")
		void rejectsTransitionWhileCancelRequested() {
			// given
			Order order = orderWithStatus(OrderStatus.PAID);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderRepository.findWithItemsAndMemberById(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(
					PaymentFixture.withStatus(PaymentFixture.approved(order), PaymentStatus.CANCEL_REQUESTED)));

			// when & then
			assertThatThrownBy(() -> service.changeStatus(ADMIN_ID, ORDER_ID,
					new AdminOrderStatusChangeRequest(OrderStatus.PREPARING)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_CANCEL_IN_PROGRESS);
		}
	}

	private Order orderWithStatus(OrderStatus status) {
		Order order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 1), ORDER_ID);
		ReflectionTestUtils.setField(order, "status", status);
		return order;
	}
}
