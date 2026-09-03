package com.groove.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditTargetType;
import com.groove.admin.service.AdminAuditLogService;
import com.groove.coupon.dto.AdminCouponResponse;
import com.groove.coupon.dto.CouponCreateRequest;
import com.groove.coupon.dto.CouponUpdateRequest;
import com.groove.coupon.entity.Coupon;
import com.groove.coupon.entity.CouponStatus;
import com.groove.coupon.entity.DiscountType;
import com.groove.coupon.repository.CouponRepository;
import com.groove.fixture.CouponFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;

@ExtendWith(MockitoExtension.class)
class AdminCouponServiceTest {

	private static final Long ADMIN_ID = 1L;
	private static final Long COUPON_ID = 100L;

	@Mock
	CouponRepository couponRepository;

	@Mock
	AdminAuditLogService adminAuditLogService;

	AdminCouponService adminCouponService;

	@BeforeEach
	void setUp() {
		adminCouponService = new AdminCouponService(couponRepository, adminAuditLogService);
	}

	@Nested
	@DisplayName("create()")
	class Create {

		@Test
		@DisplayName("코드가 중복되면 COUPON_CODE_DUPLICATE 예외를 던진다")
		void throwsWhenCodeDuplicated() {
			// given
			CouponCreateRequest request = createRequest("WELCOME1000", DiscountType.FIXED,
					BigDecimal.valueOf(1000));
			given(couponRepository.existsByCode("WELCOME1000")).willReturn(true);

			// when & then
			assertThatThrownBy(() -> adminCouponService.create(ADMIN_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COUPON_CODE_DUPLICATE);
			verify(couponRepository, never()).save(any());
		}

		@Test
		@DisplayName("성공하면 저장하고 감사 로그를 남긴다")
		void createsCouponAndRecordsAudit() {
			// given
			CouponCreateRequest request = createRequest("WELCOME1000", DiscountType.FIXED,
					BigDecimal.valueOf(1000));
			given(couponRepository.existsByCode("WELCOME1000")).willReturn(false);
			Coupon saved = CouponFixture.withId(CouponFixture.fixed("WELCOME1000", BigDecimal.valueOf(1000)),
					COUPON_ID);
			given(couponRepository.save(any())).willReturn(saved);

			// when
			AdminCouponResponse response = adminCouponService.create(ADMIN_ID, request);

			// then
			assertThat(response.id()).isEqualTo(COUPON_ID);
			verify(adminAuditLogService).record(ADMIN_ID, AdminAuditAction.COUPON_CREATE,
					AdminAuditTargetType.COUPON, COUPON_ID, null);
		}
	}

	@Nested
	@DisplayName("update()")
	class Update {

		@Test
		@DisplayName("존재하지 않는 쿠폰이면 COUPON_NOT_FOUND 예외를 던진다")
		void throwsWhenCouponNotFound() {
			// given
			given(couponRepository.findById(COUPON_ID)).willReturn(Optional.empty());
			CouponUpdateRequest request = emptyUpdateRequest();

			// when & then
			assertThatThrownBy(() -> adminCouponService.update(ADMIN_ID, COUPON_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COUPON_NOT_FOUND);
		}

		@Test
		@DisplayName("변경된 필드만 감사 로그 detail 에 콤마로 이어 남긴다")
		void recordsChangedFieldsAsAuditDetail() {
			// given
			Coupon coupon = CouponFixture.withId(CouponFixture.fixed("UPDATE1000", BigDecimal.valueOf(1000)),
					COUPON_ID);
			given(couponRepository.findById(COUPON_ID)).willReturn(Optional.of(coupon));
			CouponUpdateRequest request = new CouponUpdateRequest("새 이름", null, null, null, null, null, null, null);
			ArgumentCaptor<String> detailCaptor = ArgumentCaptor.forClass(String.class);

			// when
			adminCouponService.update(ADMIN_ID, COUPON_ID, request);

			// then
			verify(adminAuditLogService).record(eq(ADMIN_ID), eq(AdminAuditAction.COUPON_UPDATE),
					eq(AdminAuditTargetType.COUPON), eq(COUPON_ID), detailCaptor.capture());
			assertThat(detailCaptor.getValue()).isEqualTo("name");
		}

		@Test
		@DisplayName("발급이 시작된 쿠폰의 할인 조건을 바꾸려 하면 COUPON_DISCOUNT_LOCKED 예외를 던진다")
		void throwsWhenDiscountLockedAfterIssued() {
			// given
			Coupon coupon = CouponFixture.withId(
					CouponFixture.withIssuedCount(CouponFixture.fixed("LOCKED1000", BigDecimal.valueOf(1000)), 1),
					COUPON_ID);
			given(couponRepository.findById(COUPON_ID)).willReturn(Optional.of(coupon));
			CouponUpdateRequest request = new CouponUpdateRequest(null, DiscountType.RATE, BigDecimal.valueOf(10),
					null, null, null, null, null);

			// when & then
			assertThatThrownBy(() -> adminCouponService.update(ADMIN_ID, COUPON_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COUPON_DISCOUNT_LOCKED);
		}

		@Test
		@DisplayName("expiresAt 을 함께 갱신하면 DISABLED 에서 ACTIVE 로 재활성화된다")
		void reactivatesWhenExpiresAtExtendedInSameRequest() {
			// given
			Coupon coupon = CouponFixture.withId(CouponFixture.fixed("REACTIVATE1", BigDecimal.valueOf(1000)),
					COUPON_ID);
			coupon.disableAndExpire();
			LocalDateTime newExpiresAt = LocalDateTime.now().plusDays(10);
			given(couponRepository.findById(COUPON_ID)).willReturn(Optional.of(coupon));
			CouponUpdateRequest request = new CouponUpdateRequest(null, null, null, null, null, null, newExpiresAt,
					CouponStatus.ACTIVE);

			// when
			AdminCouponResponse response = adminCouponService.update(ADMIN_ID, COUPON_ID, request);

			// then
			assertThat(response.status()).isEqualTo(CouponStatus.ACTIVE);
			assertThat(response.expiresAt()).isEqualTo(newExpiresAt);
		}

		@Test
		@DisplayName("만료된 상태에서 expiresAt 갱신 없이 재활성화하면 COUPON_EXPIRED 예외를 던진다")
		void throwsWhenReactivatingWithoutExtendingExpiresAt() {
			// given
			Coupon coupon = CouponFixture.withId(CouponFixture.fixed("REACTIVATE2", BigDecimal.valueOf(1000)),
					COUPON_ID);
			coupon.disableAndExpire();
			given(couponRepository.findById(COUPON_ID)).willReturn(Optional.of(coupon));
			CouponUpdateRequest request = new CouponUpdateRequest(null, null, null, null, null, null, null,
					CouponStatus.ACTIVE);

			// when & then
			assertThatThrownBy(() -> adminCouponService.update(ADMIN_ID, COUPON_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COUPON_EXPIRED);
		}
	}

	@Nested
	@DisplayName("disable()")
	class Disable {

		@Test
		@DisplayName("비활성화하고 감사 로그를 남긴다")
		void disablesCouponAndRecordsAudit() {
			// given
			Coupon coupon = CouponFixture.withId(CouponFixture.fixed("DISABLE1000", BigDecimal.valueOf(1000)),
					COUPON_ID);
			given(couponRepository.findById(COUPON_ID)).willReturn(Optional.of(coupon));

			// when
			adminCouponService.disable(ADMIN_ID, COUPON_ID);

			// then
			assertThat(coupon.getStatus()).isEqualTo(CouponStatus.DISABLED);
			verify(adminAuditLogService).record(ADMIN_ID, AdminAuditAction.COUPON_DISABLE,
					AdminAuditTargetType.COUPON, COUPON_ID, null);
		}

		@Test
		@DisplayName("이미 DISABLED 상태여도 예외 없이 멱등하게 동작한다")
		void isIdempotentWhenAlreadyDisabled() {
			// given
			Coupon coupon = CouponFixture.withId(CouponFixture.fixed("DISABLE2000", BigDecimal.valueOf(1000)),
					COUPON_ID);
			coupon.disableAndExpire();
			given(couponRepository.findById(COUPON_ID)).willReturn(Optional.of(coupon));

			// when & then
			assertThatCode(() -> adminCouponService.disable(ADMIN_ID, COUPON_ID)).doesNotThrowAnyException();
			assertThat(coupon.getStatus()).isEqualTo(CouponStatus.DISABLED);
		}

		@Test
		@DisplayName("존재하지 않는 쿠폰이면 COUPON_NOT_FOUND 예외를 던진다")
		void throwsWhenCouponNotFound() {
			// given
			given(couponRepository.findById(COUPON_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> adminCouponService.disable(ADMIN_ID, COUPON_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COUPON_NOT_FOUND);
		}
	}

	private CouponCreateRequest createRequest(String code, DiscountType discountType, BigDecimal discountValue) {
		return new CouponCreateRequest(code, "테스트 쿠폰", discountType, discountValue, null, null, null,
				LocalDateTime.now().plusDays(7));
	}

	private CouponUpdateRequest emptyUpdateRequest() {
		return new CouponUpdateRequest(null, null, null, null, null, null, null, null);
	}
}
