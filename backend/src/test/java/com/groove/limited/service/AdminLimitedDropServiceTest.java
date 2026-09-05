package com.groove.limited.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditTargetType;
import com.groove.admin.service.AdminAuditLogService;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.LimitedDropFixture;
import com.groove.fixture.LimitedPurchaseFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.PageResponse;
import com.groove.inventory.dto.StockAdjustRequest;
import com.groove.inventory.entity.StockChangeType;
import com.groove.inventory.service.StockService;
import com.groove.limited.dto.AdminLimitedDropDetailResponse;
import com.groove.limited.dto.AdminLimitedDropResponse;
import com.groove.limited.dto.AdminLimitedDropSummaryResponse;
import com.groove.limited.dto.LimitedDropCreateRequest;
import com.groove.limited.dto.LimitedDropUpdateRequest;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.entity.LimitedDropStatus;
import com.groove.limited.entity.LimitedPurchase;
import com.groove.limited.repository.LimitedDropRepository;
import com.groove.limited.repository.LimitedPurchaseRepository;
import com.groove.member.entity.Member;
import com.groove.order.entity.Order;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.ProductRepository;

@ExtendWith(MockitoExtension.class)
class AdminLimitedDropServiceTest {

	private static final Long ADMIN_ID = 1L;
	private static final Long DROP_ID = 10L;
	private static final Long PRODUCT_ID = 100L;

	@Mock
	LimitedDropRepository limitedDropRepository;

	@Mock
	LimitedPurchaseRepository limitedPurchaseRepository;

	@Mock
	ProductRepository productRepository;

	@Mock
	StockService stockService;

	@Mock
	LimitedDropRedisService limitedDropRedisService;

	@Mock
	AdminAuditLogService adminAuditLogService;

	AdminLimitedDropService adminLimitedDropService;

	Product product;

	@BeforeEach
	void setUp() {
		adminLimitedDropService = new AdminLimitedDropService(limitedDropRepository, limitedPurchaseRepository,
				productRepository, stockService, limitedDropRedisService, adminAuditLogService);
		Artist artist = ArtistFixture.withId(1L);
		product = ProductFixture.withId(ProductFixture.create(artist), PRODUCT_ID);
	}

	private LimitedDropCreateRequest createRequest() {
		return new LimitedDropCreateRequest(PRODUCT_ID, 100, 2, LocalDateTime.now().plusDays(1),
				LocalDateTime.now().plusDays(2));
	}

	@Nested
	@DisplayName("create()")
	class Create {

		@Test
		@DisplayName("상품이 존재하지 않으면 PRODUCT_NOT_FOUND 예외를 던진다")
		void throwsWhenProductNotFound() {
			// given
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> adminLimitedDropService.create(ADMIN_ID, createRequest()))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
			verify(limitedDropRepository, never()).save(any());
		}

		@Test
		@DisplayName("이미 드롭이 등록된 상품이면 LIMITED_DROP_ALREADY_EXISTS 예외를 던진다")
		void throwsWhenDropAlreadyExists() {
			// given
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
			given(limitedDropRepository.findByProductId(PRODUCT_ID))
					.willReturn(Optional.of(LimitedDropFixture.scheduled(product)));

			// when & then
			assertThatThrownBy(() -> adminLimitedDropService.create(ADMIN_ID, createRequest()))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_DROP_ALREADY_EXISTS);
			verify(limitedDropRepository, never()).save(any());
		}

		@Test
		@DisplayName("성공하면 저장하고 재고를 조정하고 감사 로그를 남긴다")
		void createsDropAndAdjustsStockAndRecordsAudit() {
			// given
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
			given(limitedDropRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.empty());
			given(limitedDropRepository.save(any()))
					.willAnswer(inv -> LimitedDropFixture.withId(inv.getArgument(0), DROP_ID));
			ArgumentCaptor<StockAdjustRequest> stockCaptor = ArgumentCaptor.forClass(StockAdjustRequest.class);

			// when
			AdminLimitedDropResponse response = adminLimitedDropService.create(ADMIN_ID, createRequest());

			// then
			verify(limitedDropRepository).save(any());
			verify(stockService).adjust(eq(PRODUCT_ID), stockCaptor.capture());
			assertThat(stockCaptor.getValue().changeType()).isEqualTo(StockChangeType.ADJUST);
			assertThat(stockCaptor.getValue().quantity()).isEqualTo(100);
			verify(adminAuditLogService).record(ADMIN_ID, AdminAuditAction.LIMITED_DROP_CREATE,
					AdminAuditTargetType.LIMITED_DROP, DROP_ID, null);
			assertThat(response.status()).isEqualTo(LimitedDropStatus.SCHEDULED);
			assertThat(response.remainingQuantity()).isEqualTo(100);
		}
	}

	@Nested
	@DisplayName("update()")
	class Update {

		@Test
		@DisplayName("존재하지 않는 드롭이면 LIMITED_DROP_NOT_FOUND 예외를 던진다")
		void throwsWhenDropNotFound() {
			// given
			given(limitedDropRepository.findWithProductById(DROP_ID)).willReturn(Optional.empty());
			LimitedDropUpdateRequest request = new LimitedDropUpdateRequest(null, null, null, null);

			// when & then
			assertThatThrownBy(() -> adminLimitedDropService.update(ADMIN_ID, DROP_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_DROP_NOT_FOUND);
		}

		@Test
		@DisplayName("OPEN 상태면 LIMITED_INVALID_STATUS 예외를 던지고 감사 로그를 남기지 않는다")
		void throwsWhenDropIsOpen() {
			// given
			LimitedDrop drop = LimitedDropFixture.withId(LimitedDropFixture.open(product, 100), DROP_ID);
			given(limitedDropRepository.findWithProductById(DROP_ID)).willReturn(Optional.of(drop));
			LimitedDropUpdateRequest request = new LimitedDropUpdateRequest(null, null, null, null);

			// when & then
			assertThatThrownBy(() -> adminLimitedDropService.update(ADMIN_ID, DROP_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_INVALID_STATUS);
			verify(adminAuditLogService, never()).record(any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("closeAt만 바꾸면 감사 로그 detail이 closeAt이고 재고는 조정하지 않는다")
		void recordsCloseAtOnlyWhenOnlyCloseAtChanged() {
			// given
			LimitedDrop drop = LimitedDropFixture.withId(LimitedDropFixture.scheduled(product), DROP_ID);
			given(limitedDropRepository.findWithProductById(DROP_ID)).willReturn(Optional.of(drop));
			LimitedDropUpdateRequest request = new LimitedDropUpdateRequest(null, null, null,
					LocalDateTime.now().plusDays(5));
			ArgumentCaptor<String> detailCaptor = ArgumentCaptor.forClass(String.class);

			// when
			adminLimitedDropService.update(ADMIN_ID, DROP_ID, request);

			// then
			verify(adminAuditLogService).record(eq(ADMIN_ID), eq(AdminAuditAction.LIMITED_DROP_UPDATE),
					eq(AdminAuditTargetType.LIMITED_DROP), eq(DROP_ID), detailCaptor.capture());
			assertThat(detailCaptor.getValue()).isEqualTo("closeAt");
			verify(stockService, never()).adjust(any(), any());
		}

		@Test
		@DisplayName("totalQuantity를 바꾸면 재고를 조정하고 감사 로그 detail에 totalQuantity가 포함된다")
		void adjustsStockWhenTotalQuantityChanged() {
			// given
			LimitedDrop drop = LimitedDropFixture.withId(LimitedDropFixture.scheduled(product), DROP_ID);
			given(limitedDropRepository.findWithProductById(DROP_ID)).willReturn(Optional.of(drop));
			LimitedDropUpdateRequest request = new LimitedDropUpdateRequest(200, null, null, null);
			ArgumentCaptor<StockAdjustRequest> stockCaptor = ArgumentCaptor.forClass(StockAdjustRequest.class);
			ArgumentCaptor<String> detailCaptor = ArgumentCaptor.forClass(String.class);

			// when
			adminLimitedDropService.update(ADMIN_ID, DROP_ID, request);

			// then
			verify(stockService).adjust(eq(PRODUCT_ID), stockCaptor.capture());
			assertThat(stockCaptor.getValue().changeType()).isEqualTo(StockChangeType.ADJUST);
			assertThat(stockCaptor.getValue().quantity()).isEqualTo(200);
			verify(adminAuditLogService).record(eq(ADMIN_ID), eq(AdminAuditAction.LIMITED_DROP_UPDATE),
					eq(AdminAuditTargetType.LIMITED_DROP), eq(DROP_ID), detailCaptor.capture());
			assertThat(detailCaptor.getValue()).contains("totalQuantity");
		}

		@Test
		@DisplayName("모든 필드가 null이면 감사 로그 detail이 빈 문자열이고 기존 값을 유지한다")
		void keepsValuesAndRecordsEmptyDetailWhenAllFieldsNull() {
			// given
			LimitedDrop drop = LimitedDropFixture.withId(LimitedDropFixture.scheduled(product), DROP_ID);
			int originalTotalQuantity = drop.getTotalQuantity();
			int originalPerMemberLimit = drop.getPerMemberLimit();
			LocalDateTime originalOpenAt = drop.getOpenAt();
			LocalDateTime originalCloseAt = drop.getCloseAt();
			given(limitedDropRepository.findWithProductById(DROP_ID)).willReturn(Optional.of(drop));
			LimitedDropUpdateRequest request = new LimitedDropUpdateRequest(null, null, null, null);
			ArgumentCaptor<String> detailCaptor = ArgumentCaptor.forClass(String.class);

			// when
			adminLimitedDropService.update(ADMIN_ID, DROP_ID, request);

			// then
			verify(adminAuditLogService).record(eq(ADMIN_ID), eq(AdminAuditAction.LIMITED_DROP_UPDATE),
					eq(AdminAuditTargetType.LIMITED_DROP), eq(DROP_ID), detailCaptor.capture());
			assertThat(detailCaptor.getValue()).isEmpty();
			assertThat(drop.getTotalQuantity()).isEqualTo(originalTotalQuantity);
			assertThat(drop.getPerMemberLimit()).isEqualTo(originalPerMemberLimit);
			assertThat(drop.getOpenAt()).isEqualTo(originalOpenAt);
			assertThat(drop.getCloseAt()).isEqualTo(originalCloseAt);
		}
	}

	@Nested
	@DisplayName("open()")
	class Open {

		@Test
		@DisplayName("존재하지 않는 드롭이면 LIMITED_DROP_NOT_FOUND 예외를 던진다")
		void throwsWhenDropNotFound() {
			// given
			given(limitedDropRepository.findWithProductById(DROP_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> adminLimitedDropService.open(ADMIN_ID, DROP_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_DROP_NOT_FOUND);
		}

		@Test
		@DisplayName("SCHEDULED 상태면 Redis 재고를 세팅하고 OPEN으로 전이하며 감사 로그를 남긴다")
		void opensDropAndInitializesStockWhenScheduled() {
			// given
			LimitedDrop drop = LimitedDropFixture.withId(LimitedDropFixture.scheduled(product), DROP_ID);
			given(limitedDropRepository.findWithProductById(DROP_ID)).willReturn(Optional.of(drop));
			ArgumentCaptor<String> detailCaptor = ArgumentCaptor.forClass(String.class);

			// when
			AdminLimitedDropResponse response = adminLimitedDropService.open(ADMIN_ID, DROP_ID);

			// then
			verify(limitedDropRedisService).initStock(DROP_ID, drop.getTotalQuantity());
			verify(adminAuditLogService).record(eq(ADMIN_ID), eq(AdminAuditAction.LIMITED_DROP_OPEN),
					eq(AdminAuditTargetType.LIMITED_DROP), eq(DROP_ID), detailCaptor.capture());
			assertThat(detailCaptor.getValue()).isEqualTo("SCHEDULED->OPEN");
			assertThat(response.status()).isEqualTo(LimitedDropStatus.OPEN);
		}

		@Test
		@DisplayName("이미 OPEN이면 Redis 재고를 다시 세팅하되 감사 로그는 남기지 않는다")
		void reinitializesStockWithoutAuditWhenAlreadyOpen() {
			// given
			LimitedDrop drop = LimitedDropFixture.withId(LimitedDropFixture.open(product, 100), DROP_ID);
			given(limitedDropRepository.findWithProductById(DROP_ID)).willReturn(Optional.of(drop));

			// when
			AdminLimitedDropResponse response = adminLimitedDropService.open(ADMIN_ID, DROP_ID);

			// then
			verify(limitedDropRedisService).initStock(DROP_ID, drop.remainingQuantity());
			verify(adminAuditLogService, never()).record(any(), any(), any(), any(), any());
			assertThat(response.status()).isEqualTo(LimitedDropStatus.OPEN);
		}

		@Test
		@DisplayName("CLOSED 상태면 LIMITED_INVALID_STATUS 예외를 던지고 Redis를 세팅하지 않는다")
		void throwsWhenDropIsClosed() {
			// given
			LimitedDrop drop = LimitedDropFixture.withId(
					LimitedDropFixture.withStatus(LimitedDropFixture.scheduled(product), LimitedDropStatus.CLOSED),
					DROP_ID);
			given(limitedDropRepository.findWithProductById(DROP_ID)).willReturn(Optional.of(drop));

			// when & then
			assertThatThrownBy(() -> adminLimitedDropService.open(ADMIN_ID, DROP_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_INVALID_STATUS);
			verify(limitedDropRedisService, never()).initStock(any(), anyInt());
		}
	}

	@Nested
	@DisplayName("close()")
	class Close {

		@Test
		@DisplayName("존재하지 않는 드롭이면 LIMITED_DROP_NOT_FOUND 예외를 던진다")
		void throwsWhenDropNotFound() {
			// given
			given(limitedDropRepository.findWithProductById(DROP_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> adminLimitedDropService.close(ADMIN_ID, DROP_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_DROP_NOT_FOUND);
		}

		@Test
		@DisplayName("OPEN 상태면 CLOSED로 전이하고 Redis 키를 지우고 감사 로그를 남긴다")
		void closesDropAndClearsRedisWhenOpen() {
			// given
			LimitedDrop drop = LimitedDropFixture.withId(LimitedDropFixture.open(product, 100), DROP_ID);
			given(limitedDropRepository.findWithProductById(DROP_ID)).willReturn(Optional.of(drop));
			ArgumentCaptor<String> detailCaptor = ArgumentCaptor.forClass(String.class);

			// when
			AdminLimitedDropResponse response = adminLimitedDropService.close(ADMIN_ID, DROP_ID);

			// then
			verify(limitedDropRedisService).clear(DROP_ID);
			verify(adminAuditLogService).record(eq(ADMIN_ID), eq(AdminAuditAction.LIMITED_DROP_CLOSE),
					eq(AdminAuditTargetType.LIMITED_DROP), eq(DROP_ID), detailCaptor.capture());
			assertThat(detailCaptor.getValue()).isEqualTo("OPEN->CLOSED");
			assertThat(response.status()).isEqualTo(LimitedDropStatus.CLOSED);
		}

		@Test
		@DisplayName("SOLD_OUT 상태면 감사 로그 detail이 SOLD_OUT->CLOSED이다")
		void recordsSoldOutToClosedDetailWhenSoldOut() {
			// given
			LimitedDrop drop = LimitedDropFixture.withId(
					LimitedDropFixture.withStatus(LimitedDropFixture.scheduled(product), LimitedDropStatus.SOLD_OUT),
					DROP_ID);
			given(limitedDropRepository.findWithProductById(DROP_ID)).willReturn(Optional.of(drop));
			ArgumentCaptor<String> detailCaptor = ArgumentCaptor.forClass(String.class);

			// when
			adminLimitedDropService.close(ADMIN_ID, DROP_ID);

			// then
			verify(adminAuditLogService).record(eq(ADMIN_ID), eq(AdminAuditAction.LIMITED_DROP_CLOSE),
					eq(AdminAuditTargetType.LIMITED_DROP), eq(DROP_ID), detailCaptor.capture());
			assertThat(detailCaptor.getValue()).isEqualTo("SOLD_OUT->CLOSED");
		}

		@Test
		@DisplayName("이미 CLOSED이면 Redis 키만 지우고 감사 로그 없이 예외 없이 동작한다")
		void isIdempotentWhenAlreadyClosed() {
			// given
			LimitedDrop drop = LimitedDropFixture.withId(
					LimitedDropFixture.withStatus(LimitedDropFixture.scheduled(product), LimitedDropStatus.CLOSED),
					DROP_ID);
			given(limitedDropRepository.findWithProductById(DROP_ID)).willReturn(Optional.of(drop));

			// when & then
			assertThatCode(() -> adminLimitedDropService.close(ADMIN_ID, DROP_ID)).doesNotThrowAnyException();
			verify(limitedDropRedisService).clear(DROP_ID);
			verify(adminAuditLogService, never()).record(any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("SCHEDULED 상태면 LIMITED_INVALID_STATUS 예외를 던지고 Redis를 지우지 않는다")
		void throwsWhenDropIsScheduled() {
			// given
			LimitedDrop drop = LimitedDropFixture.withId(LimitedDropFixture.scheduled(product), DROP_ID);
			given(limitedDropRepository.findWithProductById(DROP_ID)).willReturn(Optional.of(drop));

			// when & then
			assertThatThrownBy(() -> adminLimitedDropService.close(ADMIN_ID, DROP_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_INVALID_STATUS);
			verify(limitedDropRedisService, never()).clear(any());
		}
	}

	@Nested
	@DisplayName("getList()")
	class GetList {

		@Test
		@DisplayName("상태를 그대로 전달하고 리포지토리 결과를 페이지 응답으로 변환한다")
		void returnsPageResponseWithStatusPassedThrough() {
			// given
			AdminLimitedDropSummaryResponse summary = new AdminLimitedDropSummaryResponse(DROP_ID, PRODUCT_ID,
					product.getTitle(), 100, 0, 2, LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2),
					LimitedDropStatus.OPEN, LocalDateTime.now());
			Pageable pageable = PageRequest.of(0, 20);
			Page<AdminLimitedDropSummaryResponse> page = new PageImpl<>(List.of(summary));
			given(limitedDropRepository.findAdminSummaries(LimitedDropStatus.OPEN, pageable)).willReturn(page);

			// when
			PageResponse<AdminLimitedDropSummaryResponse> response = adminLimitedDropService.getList(
					LimitedDropStatus.OPEN, pageable);

			// then
			assertThat(response.content()).hasSize(1);
			verify(limitedDropRepository).findAdminSummaries(LimitedDropStatus.OPEN, pageable);
		}
	}

	@Nested
	@DisplayName("getDetail()")
	class GetDetail {

		@Test
		@DisplayName("존재하지 않는 드롭이면 LIMITED_DROP_NOT_FOUND 예외를 던진다")
		void throwsWhenDropNotFound() {
			// given
			given(limitedDropRepository.findWithProductById(DROP_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> adminLimitedDropService.getDetail(DROP_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.LIMITED_DROP_NOT_FOUND);
		}

		@Test
		@DisplayName("Redis 값이 있으면 redisRemaining에 그 값을 채운다")
		void fillsRedisRemainingWhenPresent() {
			// given
			LimitedDrop drop = LimitedDropFixture.withId(LimitedDropFixture.open(product, 100), DROP_ID);
			given(limitedDropRepository.findWithProductById(DROP_ID)).willReturn(Optional.of(drop));
			given(limitedDropRedisService.getStock(DROP_ID)).willReturn(Optional.of(80));
			given(limitedPurchaseRepository.findAllWithMemberAndOrderByDropId(DROP_ID)).willReturn(List.of());

			// when
			AdminLimitedDropDetailResponse response = adminLimitedDropService.getDetail(DROP_ID);

			// then
			assertThat(response.redisRemaining()).isEqualTo(80);
			assertThat(response.dbRemaining()).isEqualTo(drop.remainingQuantity());
		}

		@Test
		@DisplayName("Redis 값이 없으면 redisRemaining이 null이다")
		void leavesRedisRemainingNullWhenAbsent() {
			// given
			LimitedDrop drop = LimitedDropFixture.withId(LimitedDropFixture.scheduled(product), DROP_ID);
			given(limitedDropRepository.findWithProductById(DROP_ID)).willReturn(Optional.of(drop));
			given(limitedDropRedisService.getStock(DROP_ID)).willReturn(Optional.empty());
			given(limitedPurchaseRepository.findAllWithMemberAndOrderByDropId(DROP_ID)).willReturn(List.of());

			// when
			AdminLimitedDropDetailResponse response = adminLimitedDropService.getDetail(DROP_ID);

			// then
			assertThat(response.redisRemaining()).isNull();
		}

		@Test
		@DisplayName("구매자 목록을 매핑하며 주문이 없는 구매도 order 필드가 null인 채로 포함한다")
		void mapsPurchasesIncludingOneWithoutOrder() {
			// given
			LimitedDrop drop = LimitedDropFixture.withId(LimitedDropFixture.open(product, 100), DROP_ID);
			given(limitedDropRepository.findWithProductById(DROP_ID)).willReturn(Optional.of(drop));
			given(limitedDropRedisService.getStock(DROP_ID)).willReturn(Optional.of(90));

			Member memberWithoutOrder = MemberFixture.withId(MemberFixture.create("no-order@groove.com", "구매자1"),
					5L);
			LimitedPurchase purchaseWithoutOrder = LimitedPurchaseFixture.create(drop, memberWithoutOrder);

			Member memberWithOrder = MemberFixture.withId(MemberFixture.create("with-order@groove.com", "구매자2"), 6L);
			Order order = OrderFixture.withId(OrderFixture.create(memberWithOrder), 20L);
			LimitedPurchase purchaseWithOrder = LimitedPurchaseFixture.create(drop, memberWithOrder, order, 1);

			given(limitedPurchaseRepository.findAllWithMemberAndOrderByDropId(DROP_ID))
					.willReturn(List.of(purchaseWithoutOrder, purchaseWithOrder));

			// when
			AdminLimitedDropDetailResponse response = adminLimitedDropService.getDetail(DROP_ID);

			// then
			assertThat(response.purchases()).hasSize(2);
			assertThat(response.purchases().get(0).memberNickname()).isEqualTo("구매자1");
			assertThat(response.purchases().get(0).orderId()).isNull();
			assertThat(response.purchases().get(0).orderNumber()).isNull();
			assertThat(response.purchases().get(1).memberNickname()).isEqualTo("구매자2");
			assertThat(response.purchases().get(1).orderId()).isEqualTo(20L);
			assertThat(response.purchases().get(1).orderNumber()).isEqualTo(order.getOrderNumber());
		}
	}
}
