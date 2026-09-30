package com.groove.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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
import com.groove.order.dto.AdminOrderClaimCompleteRequest;
import com.groove.order.dto.AdminOrderClaimCountResponse;
import com.groove.order.dto.AdminOrderClaimRejectRequest;
import com.groove.order.dto.AdminOrderClaimSearchRequest;
import com.groove.order.dto.AdminOrderClaimSummaryResponse;
import com.groove.order.dto.AdminOrderItemCancelRequest;
import com.groove.order.dto.AdminOrderItemResponse;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderClaim;
import com.groove.order.entity.OrderClaimStatus;
import com.groove.order.entity.OrderClaimType;
import com.groove.order.entity.OrderItem;
import com.groove.order.entity.OrderItemClaimStatus;
import com.groove.order.entity.OrderItemStatus;
import com.groove.order.repository.OrderClaimCountRow;
import com.groove.order.repository.OrderClaimRepository;
import com.groove.order.repository.OrderItemRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.entity.ProductImage;
import com.groove.product.repository.ProductImageRepository;

@ExtendWith(MockitoExtension.class)
class AdminOrderClaimServiceTest {

	private static final Long ADMIN_ID = 1L;
	private static final Long ORDER_ID = 10L;
	private static final Long ITEM_ID = 100L;
	private static final Long CLAIM_ID = 500L;
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 29, 12, 0);

	@Mock
	OrderClaimRepository orderClaimRepository;

	@Mock
	OrderClaimWriter writer;

	@Mock
	OrderClaimRefundHook refundHook;

	@Mock
	OrderItemRepository orderItemRepository;

	@Mock
	ProductImageRepository productImageRepository;

	@Mock
	AdminAuditLogService adminAuditLogService;

	AdminOrderClaimService service;
	OrderItem item;

	@BeforeEach
	void setUp() {
		service = new AdminOrderClaimService(orderClaimRepository, writer, refundHook, orderItemRepository,
				productImageRepository, adminAuditLogService);
		Member member = MemberFixture.create();
		Artist artist = ArtistFixture.withId(1L);
		Product product = ProductFixture.withId(ProductFixture.create(artist), 200L);
		Order order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 1), ORDER_ID);
		item = order.getItems().get(0);
		ReflectionTestUtils.setField(item, "id", ITEM_ID);
	}

	/** 응답을 실제로 만드는(buildItemResponse 를 타는) 테스트에서만 스텁한다. */
	private void stubItemResponseLookup() {
		given(productImageRepository.findAllByProductIdInAndSortOrder(any(), eq(0))).willReturn(List.of());
		given(orderItemRepository.findWithProductById(ITEM_ID)).willReturn(Optional.of(item));
	}

	private static OrderClaimCountRow row(OrderClaimType type, OrderClaimStatus status, long count) {
		return new OrderClaimCountRow() {

			@Override
			public OrderClaimType getType() {
				return type;
			}

			@Override
			public OrderClaimStatus getStatus() {
				return status;
			}

			@Override
			public long getCount() {
				return count;
			}
		};
	}

	@Nested
	@DisplayName("getCounts()")
	class GetCounts {

		@Test
		@DisplayName("행이 없는 상태는 0 으로 채우고 total 은 다섯 상태의 합이다")
		void fillsMissingStatusesWithZero() {
			// given
			given(orderClaimRepository.countByTypeAndStatus()).willReturn(List.of(
					row(OrderClaimType.CANCEL, OrderClaimStatus.REQUESTED, 3L),
					row(OrderClaimType.CANCEL, OrderClaimStatus.DONE, 5L),
					row(OrderClaimType.CANCEL, OrderClaimStatus.WITHDRAWN, 2L)));

			// when
			AdminOrderClaimCountResponse response = service.getCounts();

			// then
			assertThat(response.cancel().requested()).isEqualTo(3L);
			assertThat(response.cancel().collecting()).isZero();
			assertThat(response.cancel().done()).isEqualTo(5L);
			assertThat(response.cancel().rejected()).isZero();
			assertThat(response.cancel().withdrawn()).isEqualTo(2L);
			assertThat(response.cancel().total()).isEqualTo(10L);
		}

		@Test
		@DisplayName("취소와 반품 건수를 섞지 않고 각각 집계한다")
		void separatesCancelAndReturn() {
			// given
			given(orderClaimRepository.countByTypeAndStatus()).willReturn(List.of(
					row(OrderClaimType.CANCEL, OrderClaimStatus.REQUESTED, 1L),
					row(OrderClaimType.RETURN, OrderClaimStatus.REQUESTED, 4L),
					row(OrderClaimType.RETURN, OrderClaimStatus.COLLECTING, 2L),
					row(OrderClaimType.RETURN, OrderClaimStatus.REJECTED, 1L)));

			// when
			AdminOrderClaimCountResponse response = service.getCounts();

			// then
			assertThat(response.cancel().requested()).isEqualTo(1L);
			assertThat(response.cancel().total()).isEqualTo(1L);
			assertThat(response.returns().requested()).isEqualTo(4L);
			assertThat(response.returns().collecting()).isEqualTo(2L);
			assertThat(response.returns().rejected()).isEqualTo(1L);
			assertThat(response.returns().total()).isEqualTo(7L);
		}

		@Test
		@DisplayName("클레임이 하나도 없으면 모두 0 이다")
		void returnsAllZeroWhenEmpty() {
			// given
			given(orderClaimRepository.countByTypeAndStatus()).willReturn(List.of());

			// when
			AdminOrderClaimCountResponse response = service.getCounts();

			// then
			assertThat(response.cancel().total()).isZero();
			assertThat(response.returns().total()).isZero();
		}
	}

	@Nested
	@DisplayName("approve()")
	class Approve {

		@Test
		@DisplayName("REQUESTED 인 CANCEL 클레임을 승인하면 환불을 시도하고 감사 로그를 남긴다")
		void approvesCancelClaim() {
			// given
			OrderClaim claim = OrderClaim.requestCancel(item, "사유", null, NOW.minusMinutes(5));
			ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
			given(writer.lockApprovable(CLAIM_ID)).willReturn(claim);
			stubItemResponseLookup();

			// when
			AdminOrderItemResponse response = service.approve(ADMIN_ID, CLAIM_ID);

			// then
			verify(refundHook).refund(ORDER_ID, CLAIM_ID, item.getRefundableAmount(), "사유", null);
			verify(adminAuditLogService).record(eq(ADMIN_ID), eq(AdminAuditAction.ORDER_STATUS_CHANGE),
					eq(AdminAuditTargetType.ORDER), eq(ORDER_ID), any());
			assertThat(response.productOrderNumber()).isEqualTo(item.getProductOrderNumber());
		}

		@Test
		@DisplayName("승인 가능 검사(lockApprovable)가 실패하면 그 예외를 그대로 던지고 환불을 시도하지 않는다")
		void throwsWhenClaimNotApprovable() {
			// given
			given(writer.lockApprovable(CLAIM_ID))
					.willThrow(new BusinessException(ErrorCode.ORDER_CLAIM_NOT_ALLOWED));

			// when & then
			assertThatThrownBy(() -> service.approve(ADMIN_ID, CLAIM_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_CLAIM_NOT_ALLOWED);
			verify(refundHook, never()).refund(any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("이미 환불이 결과를 기다리는 클레임이면 ORDER_CLAIM_REFUND_IN_PROGRESS 예외를 던지고 환불을 다시 내지 않는다")
		void throwsWhenRefundAlreadyPending() {
			// given
			given(writer.lockApprovable(CLAIM_ID))
					.willThrow(new BusinessException(ErrorCode.ORDER_CLAIM_REFUND_IN_PROGRESS));

			// when & then
			assertThatThrownBy(() -> service.approve(ADMIN_ID, CLAIM_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_CLAIM_REFUND_IN_PROGRESS);
			verify(refundHook, never()).refund(any(), any(), any(), any(), any());
			verify(adminAuditLogService, never()).record(any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("응답을 만들 상품주문을 찾을 수 없으면 ORDER_NOT_FOUND 예외를 던진다")
		void throwsWhenItemMissingForResponse() {
			// given
			OrderClaim claim = OrderClaim.requestCancel(item, "사유", null, NOW.minusMinutes(5));
			ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
			given(writer.lockApprovable(CLAIM_ID)).willReturn(claim);
			given(orderItemRepository.findWithProductById(ITEM_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> service.approve(ADMIN_ID, CLAIM_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_NOT_FOUND);
		}

		@Test
		@DisplayName("썸네일 이미지가 있으면 응답에 채운다")
		void fillsThumbnailWhenImageExists() {
			// given
			OrderClaim claim = OrderClaim.requestCancel(item, "사유", null, NOW.minusMinutes(5));
			ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
			given(writer.lockApprovable(CLAIM_ID)).willReturn(claim);
			given(orderItemRepository.findWithProductById(ITEM_ID)).willReturn(Optional.of(item));
			ProductImage image = ProductImage.of(
					item.getProduct(), "https://cdn.groove.com/cover.jpg", 0);
			given(productImageRepository.findAllByProductIdInAndSortOrder(any(), eq(0)))
					.willReturn(List.of(image));

			// when
			AdminOrderItemResponse response = service.approve(ADMIN_ID, CLAIM_ID);

			// then
			assertThat(response.thumbnailUrl()).isEqualTo("https://cdn.groove.com/cover.jpg");
		}
	}

	@Nested
	@DisplayName("getList()")
	class GetList {

		@Test
		@DisplayName("클레임 큐를 페이지 응답으로 반환한다")
		void returnsPageOfClaims() {
			// given
			OrderClaim claim = OrderClaim.requestCancel(item, "사유", null, NOW.minusMinutes(5));
			ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
			AdminOrderClaimSearchRequest request = new AdminOrderClaimSearchRequest(OrderClaimType.CANCEL,
					OrderClaimStatus.REQUESTED, 0, 20);
			Page<OrderClaim> page = new PageImpl<>(List.of(claim));
			given(orderClaimRepository.search(OrderClaimType.CANCEL, OrderClaimStatus.REQUESTED,
					request.toPageable())).willReturn(page);

			// when
			PageResponse<AdminOrderClaimSummaryResponse> result = service.getList(request);

			// then
			assertThat(result.content()).hasSize(1);
			assertThat(result.content().get(0).claimId()).isEqualTo(CLAIM_ID);
		}
	}

	@Nested
	@DisplayName("reject()")
	class Reject {

		@Test
		@DisplayName("클레임을 거부하고 감사 로그를 남긴다")
		void rejectsClaim() {
			// given
			OrderClaim claim = OrderClaim.requestCancel(item, "사유", null, NOW.minusMinutes(5));
			ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
			ReflectionTestUtils.setField(claim, "status", OrderClaimStatus.REJECTED);
			given(writer.reject(CLAIM_ID, "이미 발송 완료")).willReturn(claim);
			stubItemResponseLookup();

			// when
			AdminOrderItemResponse response = service.reject(ADMIN_ID, CLAIM_ID,
					new AdminOrderClaimRejectRequest("이미 발송 완료"));

			// then
			verify(adminAuditLogService).record(eq(ADMIN_ID), eq(AdminAuditAction.ORDER_STATUS_CHANGE),
					eq(AdminAuditTargetType.ORDER), eq(ORDER_ID), any());
			assertThat(response.productOrderNumber()).isEqualTo(item.getProductOrderNumber());
		}
	}

	@Nested
	@DisplayName("collect()")
	class Collect {

		@Test
		@DisplayName("반품 수거 시작을 감사 로그로 남긴다")
		void startsCollecting() {
			// given
			OrderClaim claim = OrderClaim.requestReturn(item, "사유", NOW.minusMinutes(5));
			ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
			given(writer.startCollecting(CLAIM_ID)).willReturn(claim);
			stubItemResponseLookup();

			// when
			service.collect(ADMIN_ID, CLAIM_ID);

			// then
			verify(adminAuditLogService).record(eq(ADMIN_ID), eq(AdminAuditAction.ORDER_STATUS_CHANGE),
					eq(AdminAuditTargetType.ORDER), eq(ORDER_ID), any());
		}
	}

	@Nested
	@DisplayName("complete()")
	class Complete {

		@Test
		@DisplayName("COLLECTING 인 RETURN 클레임을 완료 처리하면 환불을 시도한다")
		void completesReturnClaim() {
			// given
			OrderClaim claim = OrderClaim.requestReturn(item, "사유", NOW.minusMinutes(10));
			ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
			ReflectionTestUtils.setField(claim, "status", OrderClaimStatus.COLLECTING);
			given(writer.chooseRestock(CLAIM_ID, true)).willReturn(claim);
			stubItemResponseLookup();

			// when
			service.complete(ADMIN_ID, CLAIM_ID, new AdminOrderClaimCompleteRequest(true));

			// then
			verify(refundHook).refund(ORDER_ID, CLAIM_ID, item.getRefundableAmount(), "사유", null);
		}

		@Test
		@DisplayName("COLLECTING 이 아니면 ORDER_CLAIM_NOT_ALLOWED 예외를 던진다")
		void throwsWhenNotCollecting() {
			// given
			OrderClaim claim = OrderClaim.requestReturn(item, "사유", NOW.minusMinutes(10));
			ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
			given(writer.chooseRestock(CLAIM_ID, true)).willThrow(new BusinessException(
					ErrorCode.ORDER_CLAIM_NOT_ALLOWED));

			// when & then
			assertThatThrownBy(
							() -> service.complete(ADMIN_ID, CLAIM_ID, new AdminOrderClaimCompleteRequest(true)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_CLAIM_NOT_ALLOWED);
		}

		@Test
		@DisplayName("chooseRestock 이 반환한 클레임이 CANCEL 타입이면 ORDER_CLAIM_NOT_ALLOWED 예외를 던진다")
		void throwsWhenReturnedClaimIsNotReturnType() {
			// given: writer.chooseRestock 은 목이라 실제 엔티티 검증을 타지 않는다 - 이 서비스 자체의 가드를 확인한다
			OrderClaim claim = OrderClaim.requestCancel(item, "사유", null, NOW.minusMinutes(10));
			ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
			given(writer.chooseRestock(CLAIM_ID, true)).willReturn(claim);

			// when & then
			assertThatThrownBy(
							() -> service.complete(ADMIN_ID, CLAIM_ID, new AdminOrderClaimCompleteRequest(true)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_CLAIM_NOT_ALLOWED);
			verify(refundHook, never()).refund(any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("RETURN 클레임이어도 COLLECTING 이 아니면 ORDER_CLAIM_NOT_ALLOWED 예외를 던진다")
		void throwsWhenReturnClaimNotCollecting() {
			// given
			OrderClaim claim = OrderClaim.requestReturn(item, "사유", NOW.minusMinutes(10));
			ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
			given(writer.chooseRestock(CLAIM_ID, true)).willReturn(claim);

			// when & then
			assertThatThrownBy(
							() -> service.complete(ADMIN_ID, CLAIM_ID, new AdminOrderClaimCompleteRequest(true)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_CLAIM_NOT_ALLOWED);
			verify(refundHook, never()).refund(any(), any(), any(), any(), any());
		}
	}

	@Nested
	@DisplayName("cancelItemBySale()")
	class CancelItemBySale {

		@Test
		@DisplayName("상품주문 id 로 주문을 찾아 즉시 판매취소를 시도한다")
		void cancelsImmediately() {
			// given
			given(orderItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));
			given(writer.requestAdminCancel(ORDER_ID, ITEM_ID, "재고 확인 불가"))
					.willReturn(new OrderClaimRequestResult(CLAIM_ID, ITEM_ID, ORDER_ID,
							item.getRefundableAmount(), true));
			stubItemResponseLookup();

			// when
			service.cancelItemBySale(ADMIN_ID, ITEM_ID,
					new AdminOrderItemCancelRequest("재고 확인 불가"));

			// then
			verify(refundHook).refund(ORDER_ID, CLAIM_ID, item.getRefundableAmount(), "재고 확인 불가", null);
		}

		@Test
		@DisplayName("요청 바디가 없으면 사유 없이 판매취소를 시도한다")
		void cancelsWithoutRequestBody() {
			// given
			given(orderItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));
			given(writer.requestAdminCancel(ORDER_ID, ITEM_ID, null))
					.willReturn(new OrderClaimRequestResult(CLAIM_ID, ITEM_ID, ORDER_ID,
							item.getRefundableAmount(), true));
			stubItemResponseLookup();

			// when
			service.cancelItemBySale(ADMIN_ID, ITEM_ID, null);

			// then
			verify(refundHook).refund(ORDER_ID, CLAIM_ID, item.getRefundableAmount(), null, null);
		}

		@Test
		@DisplayName("환불이 요청 기록 전에 실패하면 방금 만든 판매취소 클레임을 정리하고 예외를 그대로 던진다")
		void discardsClaimWhenRefundFails() {
			// given
			BusinessException failure = new BusinessException(ErrorCode.PAYMENT_CANCEL_IN_PROGRESS);
			given(orderItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));
			given(writer.requestAdminCancel(ORDER_ID, ITEM_ID, "재고 확인 불가"))
					.willReturn(new OrderClaimRequestResult(CLAIM_ID, ITEM_ID, ORDER_ID,
							item.getRefundableAmount(), true));
			willThrow(failure).given(refundHook).refund(any(), any(), any(), any(), any());

			// when & then
			assertThatThrownBy(() -> service.cancelItemBySale(ADMIN_ID, ITEM_ID,
					new AdminOrderItemCancelRequest("재고 확인 불가")))
					.isSameAs(failure);
			verify(writer).discardUnstartedClaim(CLAIM_ID);
			verify(adminAuditLogService, never()).record(any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("정리마저 실패하면 그 예외를 suppressed 로 붙여 원래 예외를 던진다")
		void addsSuppressedWhenDiscardFails() {
			// given
			BusinessException failure = new BusinessException(ErrorCode.PAYMENT_CANCEL_IN_PROGRESS);
			IllegalStateException discardFailure = new IllegalStateException("discard");
			given(orderItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));
			given(writer.requestAdminCancel(ORDER_ID, ITEM_ID, null))
					.willReturn(new OrderClaimRequestResult(CLAIM_ID, ITEM_ID, ORDER_ID,
							item.getRefundableAmount(), true));
			willThrow(failure).given(refundHook).refund(any(), any(), any(), any(), any());
			willThrow(discardFailure).given(writer).discardUnstartedClaim(CLAIM_ID);

			// when & then
			assertThatThrownBy(() -> service.cancelItemBySale(ADMIN_ID, ITEM_ID, null))
					.isSameAs(failure)
					.hasSuppressedException(discardFailure);
		}

		@Test
		@DisplayName("상품주문을 찾을 수 없으면 COMMON_RESOURCE_NOT_FOUND 예외를 던진다")
		void throwsWhenItemNotFound() {
			// given
			given(orderItemRepository.findById(ITEM_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> service.cancelItemBySale(ADMIN_ID, ITEM_ID,
					new AdminOrderItemCancelRequest("재고 확인 불가")))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_RESOURCE_NOT_FOUND);
			verify(writer, never()).requestAdminCancel(any(), any(), any());
		}
	}
}
