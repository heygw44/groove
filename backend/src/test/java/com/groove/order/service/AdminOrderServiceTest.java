package com.groove.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditTargetType;
import com.groove.admin.service.AdminAuditLogService;
import com.groove.coupon.entity.MemberCoupon;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.CouponFixture;
import com.groove.fixture.MemberCouponFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.PageResponse;
import com.groove.limited.service.LimitedPurchaseWriter;
import com.groove.limited.service.LimitedRelease;
import com.groove.limited.service.LimitedReleaseSynchronizer;
import com.groove.member.entity.Member;
import com.groove.order.dto.AdminOrderDetailResponse;
import com.groove.order.dto.AdminOrderSearchRequest;
import com.groove.order.dto.AdminOrderStatusChangeRequest;
import com.groove.order.dto.AdminOrderSummaryResponse;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderStatus;
import com.groove.order.mapper.OrderQueryMapper;
import com.groove.order.repository.OrderRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;

@ExtendWith(MockitoExtension.class)
class AdminOrderServiceTest {

	private static final Long ADMIN_ID = 1L;
	private static final Long ORDER_ID = 500L;
	private static final Long PAYMENT_ID = 900L;

	@Mock
	OrderRepository orderRepository;

	@Mock
	OrderQueryMapper orderQueryMapper;

	@Mock
	OrderStockService orderStockService;

	@Mock
	PaymentCancelHook paymentCancelHook;

	@Mock
	AdminAuditLogService adminAuditLogService;

	@Mock
	LimitedPurchaseWriter limitedPurchaseWriter;

	@Mock
	LimitedReleaseSynchronizer limitedReleaseSynchronizer;

	AdminOrderService adminOrderService;

	Member member;
	Product product;
	LocalDateTime now;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-09-04T03:00:00Z"), ZoneId.of("Asia/Seoul"));
		now = LocalDateTime.now(clock);
		adminOrderService = new AdminOrderService(orderRepository, orderQueryMapper, orderStockService,
				paymentCancelHook, adminAuditLogService, limitedPurchaseWriter, limitedReleaseSynchronizer, clock);

		member = MemberFixture.withId(MemberFixture.create(), 1L);
		Artist artist = ArtistFixture.withId(1L);
		product = ProductFixture.withId(ProductFixture.create(artist), 100L);
	}

	@Nested
	@DisplayName("getList()")
	class GetList {

		@Test
		@DisplayName("조건에 맞는 주문이 없으면 매퍼를 호출하지 않고 빈 페이지를 반환한다")
		void returnsEmptyPageWhenNoOrders() {
			// given
			given(orderQueryMapper.countAdminOrders(any())).willReturn(0L);
			AdminOrderSearchRequest request = new AdminOrderSearchRequest(null, null, null, null, null, null);

			// when
			PageResponse<AdminOrderSummaryResponse> response = adminOrderService.getList(request);

			// then
			assertThat(response.content()).isEmpty();
			assertThat(response.totalElements()).isZero();
			verify(orderQueryMapper, never()).findAdminOrders(any());
		}

		@Test
		@DisplayName("조건에 맞는 주문이 있으면 목록과 총 개수를 반환한다")
		void returnsOrdersWhenPresent() {
			// given
			AdminOrderSummaryResponse summary = new AdminOrderSummaryResponse(ORDER_ID, "20260903-TESTAB12",
					"buyer@groove.com", OrderStatus.PAID, new BigDecimal("30000"), 1, null);
			given(orderQueryMapper.countAdminOrders(any())).willReturn(1L);
			given(orderQueryMapper.findAdminOrders(any())).willReturn(List.of(summary));
			AdminOrderSearchRequest request = new AdminOrderSearchRequest(null, null, null, null, null, null);

			// when
			PageResponse<AdminOrderSummaryResponse> response = adminOrderService.getList(request);

			// then
			assertThat(response.content()).containsExactly(summary);
			assertThat(response.totalElements()).isEqualTo(1);
		}
	}

	@Nested
	@DisplayName("getDetail()")
	class GetDetail {

		@Test
		@DisplayName("주문이 있으면 회원 정보와 상품·배송지를 포함한 상세를 반환한다")
		void returnsDetailWithMemberEmail() {
			// given
			Order order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 1), ORDER_ID);
			given(orderRepository.findWithItemsAndMemberById(ORDER_ID)).willReturn(Optional.of(order));

			// when
			AdminOrderDetailResponse response = adminOrderService.getDetail(ORDER_ID);

			// then
			assertThat(response.memberId()).isEqualTo(member.getId());
			assertThat(response.memberEmail()).isEqualTo(member.getEmail());
			assertThat(response.items()).hasSize(1);
			assertThat(response.shippingAddress().recipientName()).isEqualTo("김그루브");
		}

		@Test
		@DisplayName("존재하지 않는 주문이면 ORDER_NOT_FOUND 예외를 던진다")
		void throwsWhenOrderNotFound() {
			// given
			given(orderRepository.findWithItemsAndMemberById(ORDER_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> adminOrderService.getDetail(ORDER_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_NOT_FOUND);
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
			AdminOrderStatusChangeRequest request = new AdminOrderStatusChangeRequest(OrderStatus.PREPARING);

			// when
			AdminOrderDetailResponse response = adminOrderService.changeStatus(ADMIN_ID, ORDER_ID, request);

			// then
			assertThat(response.status()).isEqualTo(OrderStatus.PREPARING);
			assertThat(response.memberEmail()).isEqualTo(member.getEmail());
			verify(adminAuditLogService).record(eq(ADMIN_ID), eq(AdminAuditAction.ORDER_STATUS_CHANGE),
					eq(AdminAuditTargetType.ORDER), eq(ORDER_ID), eq("PAID->PREPARING"));
			verify(adminAuditLogService, times(1)).record(any(), any(), any(), any(), any());
		}

		@ParameterizedTest
		@EnumSource(value = OrderStatus.class, names = {"PAID", "PREPARING"})
		@DisplayName("PAID·PREPARING 상태를 CANCELED 로 바꾸면 재고를 복구하고 결제 취소 훅을 호출하며 감사 로그를 두 건 남긴다")
		void restoresStockAndCallsHookWhenCancelingPaidOrPreparing(OrderStatus previous) {
			// given
			Order order = orderWithStatus(previous);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderRepository.findWithItemsAndMemberById(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentCancelHook.onPaidOrderCanceled(order)).willReturn(PAYMENT_ID);
			AdminOrderStatusChangeRequest request = new AdminOrderStatusChangeRequest(OrderStatus.CANCELED);

			// when
			adminOrderService.changeStatus(ADMIN_ID, ORDER_ID, request);

			// then
			verify(orderStockService).restore(order);
			verify(paymentCancelHook).onPaidOrderCanceled(order);
			verify(adminAuditLogService).record(eq(ADMIN_ID), eq(AdminAuditAction.ORDER_STATUS_CHANGE),
					eq(AdminAuditTargetType.ORDER), eq(ORDER_ID), eq(previous.name() + "->CANCELED"));
			verify(adminAuditLogService).record(eq(ADMIN_ID), eq(AdminAuditAction.PAYMENT_CANCEL),
					eq(AdminAuditTargetType.PAYMENT), eq(PAYMENT_ID), eq("DONE->CANCELED"));
		}

		@Test
		@DisplayName("SHIPPED 를 CANCELED 로 바꾸려 하면 ORDER_INVALID_STATUS_TRANSITION 예외를 던지고 재고를 건드리지 않는다")
		void throwsWhenTransitionNotAllowed() {
			// given
			Order order = orderWithStatus(OrderStatus.SHIPPED);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderRepository.findWithItemsAndMemberById(ORDER_ID)).willReturn(Optional.of(order));
			AdminOrderStatusChangeRequest request = new AdminOrderStatusChangeRequest(OrderStatus.CANCELED);

			// when & then
			assertThatThrownBy(() -> adminOrderService.changeStatus(ADMIN_ID, ORDER_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_INVALID_STATUS_TRANSITION);
			verify(orderStockService, never()).restore(any());
			verify(adminAuditLogService, never()).record(any(), any(), any(), any(), any());
			verify(limitedPurchaseWriter, never()).revertByOrder(any(), any());
		}

		@Test
		@DisplayName("PREPARING 을 SHIPPED 로 바꾸면 재고 복구·결제 취소 훅을 호출하지 않는다")
		void skipsStockRestoreAndHookWhenNotCanceling() {
			// given
			Order order = orderWithStatus(OrderStatus.PREPARING);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderRepository.findWithItemsAndMemberById(ORDER_ID)).willReturn(Optional.of(order));
			AdminOrderStatusChangeRequest request = new AdminOrderStatusChangeRequest(OrderStatus.SHIPPED);

			// when
			adminOrderService.changeStatus(ADMIN_ID, ORDER_ID, request);

			// then
			verify(orderStockService, never()).restore(any());
			verify(paymentCancelHook, never()).onPaidOrderCanceled(any());
		}

		@Test
		@DisplayName("존재하지 않는 주문이면 ORDER_NOT_FOUND 예외를 던진다")
		void throwsWhenOrderNotFound() {
			// given
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.empty());
			AdminOrderStatusChangeRequest request = new AdminOrderStatusChangeRequest(OrderStatus.PREPARING);

			// when & then
			assertThatThrownBy(() -> adminOrderService.changeStatus(ADMIN_ID, ORDER_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_NOT_FOUND);
		}

		@Test
		@DisplayName("쿠폰을 적용한 주문을 CANCELED 로 바꾸면 쿠폰이 미사용 상태로 복구된다")
		void restoresCouponWhenCanceling() {
			// given
			Order order = orderWithStatus(OrderStatus.PAID);
			MemberCoupon memberCoupon = MemberCouponFixture.create(member,
					CouponFixture.fixed("ADMIN5000", new BigDecimal("5000")));
			order.applyCoupon(memberCoupon, new BigDecimal("5000"));
			memberCoupon.use(order.getId());
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderRepository.findWithItemsAndMemberById(ORDER_ID)).willReturn(Optional.of(order));
			AdminOrderStatusChangeRequest request = new AdminOrderStatusChangeRequest(OrderStatus.CANCELED);

			// when
			adminOrderService.changeStatus(ADMIN_ID, ORDER_ID, request);

			// then
			assertThat(memberCoupon.isUsed()).isFalse();
		}

		@Test
		@DisplayName("한정반 주문을 CANCELED 로 바꾸면 구매 이력을 되돌리고 커밋 후 Redis 선점을 해제한다")
		void revertsLimitedPurchaseAndReleasesAfterCommitOnCancel() {
			// given
			Order order = orderWithStatus(OrderStatus.PAID);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderRepository.findWithItemsAndMemberById(ORDER_ID)).willReturn(Optional.of(order));
			LimitedRelease release = new LimitedRelease(5L, member.getId());
			given(limitedPurchaseWriter.revertByOrder(ORDER_ID, now)).willReturn(Optional.of(release));
			AdminOrderStatusChangeRequest request = new AdminOrderStatusChangeRequest(OrderStatus.CANCELED);

			// when
			adminOrderService.changeStatus(ADMIN_ID, ORDER_ID, request);

			// then
			verify(limitedReleaseSynchronizer).releaseAfterCommit(release);
		}

		@Test
		@DisplayName("CANCELED 가 아닌 전이면 한정반 구매 이력을 되돌리지 않는다")
		void skipsLimitedRevertWhenNotCanceling() {
			// given
			Order order = orderWithStatus(OrderStatus.PAID);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderRepository.findWithItemsAndMemberById(ORDER_ID)).willReturn(Optional.of(order));
			AdminOrderStatusChangeRequest request = new AdminOrderStatusChangeRequest(OrderStatus.PREPARING);

			// when
			adminOrderService.changeStatus(ADMIN_ID, ORDER_ID, request);

			// then
			verify(limitedPurchaseWriter, never()).revertByOrder(any(), any());
		}
	}

	private Order orderWithStatus(OrderStatus status) {
		Order order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 1), ORDER_ID);
		ReflectionTestUtils.setField(order, "status", status);
		return order;
	}
}
