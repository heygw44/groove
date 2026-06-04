package com.groove.admin.api;

import com.groove.admin.api.dto.AdminCouponCreateRequest;
import com.groove.admin.api.dto.AdminCouponGrantRequest;
import com.groove.admin.api.dto.AdminCouponStatusChangeRequest;
import com.groove.admin.api.dto.AdminCouponSummary;
import com.groove.admin.api.dto.AdminMemberCouponResponse;
import com.groove.common.api.PageResponse;
import com.groove.common.api.SortValidator;
import com.groove.coupon.application.AdminCouponService;
import com.groove.coupon.domain.Coupon;
import com.groove.coupon.domain.CouponStatus;
import com.groove.coupon.domain.MemberCoupon;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * 관리자 쿠폰 CRUD · 직접지급 API (이슈 #92, API.md §3.10).
 *
 * <p>인가 경계는 {@code SecurityConfig} 의 {@code /api/v1/admin/**} → {@code hasRole("ADMIN")} 가
 * 담당하므로 컨트롤러에 별도 권한 어노테이션을 두지 않는다 — {@code AdminOrderController} 와 동일 패턴.
 *
 * <p>정렬 화이트리스트: {@code id}, {@code validUntil} 만 허용 — 인덱스 없는 컬럼 정렬 차단.
 */
@RestController
@RequestMapping("/api/v1/admin/coupons")
@Validated
public class AdminCouponController {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of("id", "validUntil");

    private final AdminCouponService adminCouponService;

    public AdminCouponController(AdminCouponService adminCouponService) {
        this.adminCouponService = adminCouponService;
    }

    @PostMapping
    public ResponseEntity<AdminCouponSummary> create(@Valid @RequestBody AdminCouponCreateRequest request) {
        Coupon created = adminCouponService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(AdminCouponSummary.from(created));
    }

    @GetMapping
    public ResponseEntity<PageResponse<AdminCouponSummary>> list(
            @RequestParam(required = false) CouponStatus status,
            @PageableDefault(size = 20)
            @SortDefault(sort = "id", direction = Sort.Direction.DESC)
            Pageable pageable) {
        SortValidator.requireAllowed(pageable.getSort(), ALLOWED_SORT_PROPERTIES);
        Page<Coupon> page = adminCouponService.list(status, pageable);
        return ResponseEntity.ok(PageResponse.from(page, AdminCouponSummary::from));
    }

    @PatchMapping("/{couponId}/status")
    public ResponseEntity<AdminCouponSummary> changeStatus(
            @PathVariable @Positive Long couponId,
            @Valid @RequestBody AdminCouponStatusChangeRequest request) {
        Coupon coupon = adminCouponService.changeStatus(couponId, request.target());
        return ResponseEntity.ok(AdminCouponSummary.from(coupon));
    }

    @PostMapping("/{couponId}/grant")
    public ResponseEntity<AdminMemberCouponResponse> grant(
            @PathVariable @Positive Long couponId,
            @Valid @RequestBody AdminCouponGrantRequest request) {
        MemberCoupon granted = adminCouponService.grant(couponId, request.memberId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AdminMemberCouponResponse.from(granted));
    }
}
