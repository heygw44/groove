package com.groove.coupon.api;

import com.groove.auth.security.AuthPrincipal;
import com.groove.common.api.PageResponse;
import com.groove.common.api.SortValidator;
import com.groove.coupon.api.dto.MemberCouponResponse;
import com.groove.coupon.application.CouponQueryService;
import com.groove.coupon.domain.MemberCouponStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * 회원 본인 보유 쿠폰 목록 (API.md §3.9 — GET /me/coupons).
 *
 * <p>{@link CouponController} 와 분리한 이유는 URL prefix({@code /me/coupons})가 다르고 항상 인증된
 * 회원에게만 노출되기 때문이다 — SecurityConfig 의 {@code anyRequest().authenticated()} 기본 정책을
 * 그대로 따라간다 ({@code MemberOrderController} 와 동일 구조).
 *
 * <p>정렬 화이트리스트: {@code issuedAt} 만 허용.
 */
@RestController
@RequestMapping("/api/v1/me/coupons")
public class MyCouponController {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of("issuedAt");

    private final CouponQueryService couponQueryService;

    public MyCouponController(CouponQueryService couponQueryService) {
        this.couponQueryService = couponQueryService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<MemberCouponResponse>> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) MemberCouponStatus status,
            @PageableDefault(size = 20)
            @SortDefault(sort = "issuedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        SortValidator.requireAllowed(pageable.getSort(), ALLOWED_SORT_PROPERTIES);

        Page<MemberCouponResponse> page = couponQueryService.listForMember(principal.memberId(), status, pageable);
        return ResponseEntity.ok(PageResponse.of(page));
    }
}
