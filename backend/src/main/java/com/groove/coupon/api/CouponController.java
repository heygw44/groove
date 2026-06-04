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
import jakarta.servlet.http.HttpServletRequest;
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
 * 쿠폰 API (API.md §3.9).
 *
 * <p>{@code GET /coupons} 는 발급 가능 목록(Public). {@code POST /coupons/{id}/issue} 는 선착순 발급으로
 * USER 권한 + {@code Idempotency-Key} 헤더가 필수다 — {@link PaymentController} 와 동일한 멱등 패턴:
 * {@link IdempotencyKeyInterceptor} 가 헤더를 검증(없으면 400)하고, 처리 본체는
 * {@link IdempotencyService#execute} 로 같은 키당 한 번만 실행된다. 컨트롤러는 비트랜잭션이며
 * {@code CouponIssueService.issue} 가 자기 트랜잭션을 커밋한 뒤 멱등성 마커가 COMPLETED 로 갱신된다.
 *
 * <p>{@code POST .../issue} 는 회원당 분당 발급 횟수가 {@code CouponIssueRateLimitPolicy} 로 제한된다.
 */
@RestController
@RequestMapping("/api/v1/coupons")
public class CouponController {

    /** 발급 가능 목록 정렬 화이트리스트 — 인덱스(또는 PK) 있는 컬럼만 허용 (SortValidator 보안 패턴). */
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

    @GetMapping
    public ResponseEntity<PageResponse<CouponResponse>> listIssuable(
            @PageableDefault(size = 20)
            @SortDefault(sort = "validUntil", direction = Sort.Direction.ASC)
            Pageable pageable) {
        SortValidator.requireAllowed(pageable.getSort(), ALLOWED_SORT_PROPERTIES);

        Page<CouponResponse> page = couponQueryService.listIssuable(pageable);
        return ResponseEntity.ok(PageResponse.of(page));
    }

    @PostMapping("/{couponId}/issue")
    @Idempotent
    public ResponseEntity<MemberCouponResponse> issue(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long couponId,
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
