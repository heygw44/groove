package com.groove.coupon.api;

import com.groove.auth.security.AuthPrincipal;
import com.groove.common.api.PageResponse;
import com.groove.common.api.SortValidator;
import com.groove.coupon.api.dto.MemberCouponResponse;
import com.groove.coupon.application.CouponQueryService;
import com.groove.coupon.domain.MemberCouponStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
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
 * 회원 본인 보유 쿠폰 목록 (API.md §3.9 — GET /members/me/coupons).
 *
 * <p>{@link CouponController}(공개 {@code /coupons}) 와 분리한 이유는 항상 인증된 회원에게만 노출되기
 * 때문이다 — 본인 리소스 경로({@code /members/me/coupons})로 {@code MemberController}·
 * {@code MemberOrderController} 와 prefix 를 통일했다. SecurityConfig 의
 * {@code anyRequest().authenticated()} 기본 정책을 그대로 따라간다.
 *
 * <p>정렬 화이트리스트: {@code issuedAt} 만 허용.
 */
@Tag(name = "내 쿠폰", description = "로그인한 회원 본인이 보유한 쿠폰 목록 조회")
@RestController
@RequestMapping("/api/v1/members/me/coupons")
public class MyCouponController {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of("issuedAt");

    private final CouponQueryService couponQueryService;

    public MyCouponController(CouponQueryService couponQueryService) {
        this.couponQueryService = couponQueryService;
    }

    @Operation(summary = "내 보유 쿠폰 목록",
            description = "로그인한 회원 본인이 보유한 쿠폰을 페이지로 조회한다. status 쿼리 파라미터로 상태별 필터링이 가능하며, "
                    + "생략 시 전체를 반환한다. USED 쿠폰은 사용 주문번호(orderNumber)가 채워진다. 정렬은 issuedAt 만 허용한다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "내 쿠폰 목록 조회 성공")
    @ApiResponse(responseCode = "400", description = "허용되지 않은 정렬 속성")
    @ApiResponse(responseCode = "401", description = "인증 필요 (토큰 없음·무효·만료)")
    @GetMapping
    public ResponseEntity<PageResponse<MemberCouponResponse>> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Parameter(description = "쿠폰 상태 필터 (생략 시 전체)") @RequestParam(required = false) MemberCouponStatus status,
            @PageableDefault(size = 20)
            @SortDefault(sort = "issuedAt", direction = Sort.Direction.DESC)
            @ParameterObject Pageable pageable) {
        SortValidator.requireAllowed(pageable.getSort(), ALLOWED_SORT_PROPERTIES);

        Page<MemberCouponResponse> page = couponQueryService.listForMember(principal.memberId(), status, pageable);
        return ResponseEntity.ok(PageResponse.of(page));
    }
}
