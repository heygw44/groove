package com.groove.coupon.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditTargetType;
import com.groove.admin.service.AdminAuditLogService;
import com.groove.coupon.dto.AdminCouponResponse;
import com.groove.coupon.dto.AdminCouponSummaryResponse;
import com.groove.coupon.dto.CouponCreateRequest;
import com.groove.coupon.dto.CouponUpdateRequest;
import com.groove.coupon.entity.Coupon;
import com.groove.coupon.entity.CouponStatus;
import com.groove.coupon.entity.DiscountType;
import com.groove.coupon.repository.CouponRepository;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.PageResponse;

import lombok.RequiredArgsConstructor;

/** 관리자 쿠폰 등록/수정/비활성화/목록 조회. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminCouponService {

	private final CouponRepository couponRepository;
	private final AdminAuditLogService adminAuditLogService;

	@Transactional
	public AdminCouponResponse create(Long adminId, CouponCreateRequest request) {
		if (couponRepository.existsByCode(request.code())) {
			throw new BusinessException(ErrorCode.COUPON_CODE_DUPLICATE);
		}

		Coupon coupon = Coupon.create(request.code(), request.name(), request.discountType(),
				request.discountValue(), request.minOrderAmount(), request.maxDiscountAmount(),
				request.totalQuantity(), request.expiresAt());
		Coupon saved = couponRepository.save(coupon);

		adminAuditLogService.record(adminId, AdminAuditAction.COUPON_CREATE, AdminAuditTargetType.COUPON,
				saved.getId(), null);

		return AdminCouponResponse.from(saved);
	}

	@Transactional
	public AdminCouponResponse update(Long adminId, Long couponId, CouponUpdateRequest request) {
		Coupon coupon = couponRepository.findById(couponId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));

		List<String> changedFields = new ArrayList<>();

		if (request.discountType() != null || request.discountValue() != null || request.minOrderAmount() != null
				|| request.maxDiscountAmount() != null) {
			DiscountType discountType = coalesce(request.discountType(), coupon.getDiscountType(), "discountType",
					changedFields);
			BigDecimal discountValue = coalesce(request.discountValue(), coupon.getDiscountValue(), "discountValue",
					changedFields);
			BigDecimal minOrderAmount = coalesce(request.minOrderAmount(), coupon.getMinOrderAmount(),
					"minOrderAmount", changedFields);
			BigDecimal maxDiscountAmount = coalesce(request.maxDiscountAmount(), coupon.getMaxDiscountAmount(),
					"maxDiscountAmount", changedFields);
			coupon.updateDiscount(discountType, discountValue, minOrderAmount, maxDiscountAmount);
		}

		String name = coalesce(request.name(), coupon.getName(), "name", changedFields);
		LocalDateTime expiresAt = coalesce(request.expiresAt(), coupon.getExpiresAt(), "expiresAt", changedFields);
		Integer totalQuantity = coalesce(request.totalQuantity(), coupon.getTotalQuantity(), "totalQuantity",
				changedFields);
		coupon.updateInfo(name, expiresAt, totalQuantity);

		if (request.status() != null && request.status() != coupon.getStatus()) {
			changedFields.add("status");
			if (request.status() == CouponStatus.ACTIVE) {
				coupon.activate();
			} else {
				coupon.disable();
			}
		}

		adminAuditLogService.record(adminId, AdminAuditAction.COUPON_UPDATE, AdminAuditTargetType.COUPON, couponId,
				String.join(",", changedFields));

		return AdminCouponResponse.from(coupon);
	}

	@Transactional
	public void disable(Long adminId, Long couponId) {
		Coupon coupon = couponRepository.findById(couponId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));
		coupon.disableAndExpire();

		adminAuditLogService.record(adminId, AdminAuditAction.COUPON_DISABLE, AdminAuditTargetType.COUPON, couponId,
				null);
	}

	public PageResponse<AdminCouponSummaryResponse> getList(CouponStatus status, Pageable pageable) {
		Page<AdminCouponSummaryResponse> page = couponRepository.findAdminSummaries(status, pageable);
		return PageResponse.from(page);
	}

	private <T> T coalesce(T newValue, T currentValue, String fieldName, List<String> changedFields) {
		if (newValue == null) {
			return currentValue;
		}
		changedFields.add(fieldName);
		return newValue;
	}
}
