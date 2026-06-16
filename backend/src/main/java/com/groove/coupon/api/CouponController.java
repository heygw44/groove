package com.groove.coupon.api;

import com.groove.auth.security.AuthPrincipal;
import com.groove.common.api.PageResponse;
import com.groove.common.api.SortValidator;
import com.groove.common.idempotency.IdempotencyService;
import com.groove.common.idempotency.web.Idempotent;
import com.groove.common.idempotency.web.IdempotencyKeyInterceptor;
import com.groove.coupon.api.dto.CouponResponse;
import com.groove.coupon.api.dto.MemberCouponResponse;
import com.groove.coupon.application.CouponIssueService;
import com.groove.coupon.application.CouponQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * 쿠폰 API.
 *
 * <p>GET /coupons 는 발급 가능 목록(Public). POST /coupons/{id}/issue 는 선착순 발급으로 USER 권한 +
 * Idempotency-Key 헤더가 필수다. IdempotencyKeyInterceptor 가 헤더를 검증(없으면 400)하고, 처리 본체는
 * IdempotencyService.execute 로 같은 키당 한 번만 실행된다. 컨트롤러는 비트랜잭션이며
 * CouponIssueService.issue 가 자기 트랜잭션을 커밋한 뒤 멱등성 마커가 COMPLETED 로 갱신된다.
 *
 * <p>POST .../issue 는 회원당 분당 발급 횟수가 CouponIssueRateLimitPolicy 로 제한된다.
 */
@Tag(name = "쿠폰", description = "발급 가능 쿠폰 목록 조회(공개) · 선착순 쿠폰 발급(인증)")
@RestController
@RequestMapping("/api/v1/coupons")
public class CouponController {

    /** 발급 가능 목록 정렬 화이트리스트 — 인덱스(또는 PK) 있는 컬럼만 허용. */
    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of("validUntil", "id");

    private final CouponQueryService couponQueryService;
    private final CouponIssueService couponIssueService;
    private final IdempotencyService idempotencyService;

    public CouponController(CouponQueryService couponQueryService,
                            CouponIssueService couponIssueService,
                            IdempotencyService idempotencyService) {
        this.couponQueryService = couponQueryService;
        this.couponIssueService = couponIssueService;
        this.idempotencyService = idempotencyService;
    }

    @Operation(summary = "발급 가능 쿠폰 목록",
            description = "현재 발급 가능한(ACTIVE + 발급 기간 내) 쿠폰을 페이지로 조회한다. 비로그인 공개 엔드포인트다. "
                    + "소진 여부는 remainingQuantity 로 노출되며, 무제한 발급이면 null 이다. 정렬은 validUntil·id 만 허용한다.")
    @ApiResponse(responseCode = "200", description = "발급 가능 쿠폰 목록 조회 성공")
    @ApiResponse(responseCode = "400", description = "허용되지 않은 정렬 속성")
    @GetMapping
    public ResponseEntity<PageResponse<CouponResponse>> listIssuable(
            @PageableDefault(size = 20)
            @SortDefault(sort = "validUntil", direction = Sort.Direction.ASC)
            @ParameterObject Pageable pageable) {
        SortValidator.requireAllowed(pageable.getSort(), ALLOWED_SORT_PROPERTIES);

        Page<CouponResponse> page = couponQueryService.listIssuable(pageable);
        return ResponseEntity.ok(PageResponse.of(page));
    }

    @Operation(summary = "쿠폰 발급 (선착순)",
            description = "지정한 쿠폰을 인증 회원에게 발급한다 — 회원당 1장, 초과발급 없는 선착순이다. "
                    + "멱등 엔드포인트로 Idempotency-Key 헤더가 필수이며(없으면 400), 같은 키 재요청은 캐시된 발급 응답을 그대로 받는다. "
                    + "회원당 분당 발급 횟수가 제한되어 한도 초과 시 429 가 반환된다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "201", description = "쿠폰 발급 성공")
    @ApiResponse(responseCode = "400", description = "Idempotency-Key 헤더 누락")
    @ApiResponse(responseCode = "401", description = "인증 필요 (토큰 없음·무효·만료)")
    @ApiResponse(responseCode = "404", description = "쿠폰을 찾을 수 없음")
    @ApiResponse(responseCode = "409", description = "재고 소진 · 이미 발급받은 쿠폰 · 처리 중인 동일 키 · Idempotency-Key 재사용 충돌")
    @ApiResponse(responseCode = "422", description = "현재 발급할 수 없는 쿠폰 (비활성·발급 기간 외)")
    @ApiResponse(responseCode = "429", description = "발급 Rate Limit 초과 (회원당 분당 한도)")
    @PostMapping("/{couponId}/issue")
    @Idempotent
    public ResponseEntity<MemberCouponResponse> issue(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Parameter(description = "발급할 쿠폰 식별자") @PathVariable Long couponId,
            HttpServletRequest httpRequest) {
        String idempotencyKey = (String) httpRequest.getAttribute(IdempotencyKeyInterceptor.KEY_ATTRIBUTE);
        Long memberId = principal.memberId();
        String fingerprint = couponId + "|" + memberId;

        MemberCouponResponse response = idempotencyService.execute(
                idempotencyKey, fingerprint, MemberCouponResponse.class,
                () -> couponIssueService.issue(memberId, couponId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
