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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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
@Tag(name = "쿠폰 (관리자)", description = "쿠폰 정책 생성·조회·상태변경·직접지급 (모두 ADMIN 권한 필요)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/coupons")
@Validated
public class AdminCouponController {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of("id", "validUntil");

    private final AdminCouponService adminCouponService;

    public AdminCouponController(AdminCouponService adminCouponService) {
        this.adminCouponService = adminCouponService;
    }

    @Operation(summary = "쿠폰 정책 생성",
            description = "할인 방식·할인값·유효기간·발급 한정수량 등으로 신규 쿠폰 정책을 생성한다. ADMIN 권한 필요. "
                    + "형식 검증과 정률 1~100·validUntil>validFrom 같은 의미 검증을 모두 통과해야 한다.")
    @ApiResponse(responseCode = "201", description = "쿠폰 정책 생성 성공")
    @ApiResponse(responseCode = "400", description = "입력 검증 실패 (필수값 누락·정률 범위·유효기간 역전 등)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "미인증 — 로그인이 필요합니다",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "권한 부족 — ADMIN 권한이 없습니다",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping
    public ResponseEntity<AdminCouponSummary> create(@Valid @RequestBody AdminCouponCreateRequest request) {
        Coupon created = adminCouponService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(AdminCouponSummary.from(created));
    }

    @Operation(summary = "쿠폰 정책 목록 조회",
            description = "쿠폰 정책을 페이지 단위로 조회한다. status 로 상태 필터링 가능. ADMIN 권한 필요. "
                    + "정렬은 id·validUntil 만 허용한다.")
    @ApiResponse(responseCode = "200", description = "조회 성공 — 쿠폰 정책 페이지")
    @ApiResponse(responseCode = "400", description = "허용되지 않은 정렬 속성 등 잘못된 요청",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "미인증 — 로그인이 필요합니다",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "권한 부족 — ADMIN 권한이 없습니다",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping
    public ResponseEntity<PageResponse<AdminCouponSummary>> list(
            @Parameter(description = "쿠폰 정책 상태 필터 (ACTIVE/SUSPENDED/ENDED) — 생략 시 전체")
            @RequestParam(required = false) CouponStatus status,
            @ParameterObject
            @PageableDefault(size = 20)
            @SortDefault(sort = "id", direction = Sort.Direction.DESC)
            Pageable pageable) {
        SortValidator.requireAllowed(pageable.getSort(), ALLOWED_SORT_PROPERTIES);
        Page<Coupon> page = adminCouponService.list(status, pageable);
        return ResponseEntity.ok(PageResponse.from(page, AdminCouponSummary::from));
    }

    @Operation(summary = "쿠폰 정책 상태 변경",
            description = "쿠폰 정책 상태를 변경한다(ACTIVE↔SUSPENDED, →ENDED 등 합법 전이만). ADMIN 권한 필요. "
                    + "현재 상태와 동일한 상태로의 요청은 멱등 처리되어 현재 상태를 그대로 반환한다.")
    @ApiResponse(responseCode = "200", description = "상태 변경 성공 (또는 멱등 — 동일 상태 재요청)")
    @ApiResponse(responseCode = "400", description = "잘못된 상태값(enum 바인딩 실패) 등 입력 검증 실패",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "미인증 — 로그인이 필요합니다",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "권한 부족 — ADMIN 권한이 없습니다",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "쿠폰을 찾을 수 없음",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "불법 상태 전이 (COUPON_INVALID_STATE_TRANSITION)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PatchMapping("/{couponId}/status")
    public ResponseEntity<AdminCouponSummary> changeStatus(
            @Parameter(description = "대상 쿠폰 정책 ID", example = "7")
            @PathVariable @Positive Long couponId,
            @Valid @RequestBody AdminCouponStatusChangeRequest request) {
        Coupon coupon = adminCouponService.changeStatus(couponId, request.target());
        return ResponseEntity.ok(AdminCouponSummary.from(coupon));
    }

    @Operation(summary = "쿠폰 직접지급",
            description = "특정 회원에게 쿠폰을 직접 발급한다. ADMIN 권한 필요. 선착순 한정수량과 독립적으로 동작하며 "
                    + "정책 발급 카운터를 증가시키지 않는다. 활성 회원만 가능하고, 이미 보유한 회원에게는 발급되지 않는다.")
    @ApiResponse(responseCode = "201", description = "직접지급 성공 — 발급된 회원 쿠폰 반환")
    @ApiResponse(responseCode = "400", description = "입력 검증 실패 (memberId 누락 등)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "미인증 — 로그인이 필요합니다",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "권한 부족 — ADMIN 권한이 없습니다",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "쿠폰 또는 활성 회원을 찾을 수 없음",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "이미 해당 쿠폰을 보유한 회원 (COUPON_ALREADY_ISSUED)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "발급할 수 없는 쿠폰 상태/기간 (COUPON_NOT_ISSUABLE — SUSPENDED·ENDED·만료)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/{couponId}/grant")
    public ResponseEntity<AdminMemberCouponResponse> grant(
            @Parameter(description = "지급할 쿠폰 정책 ID", example = "7")
            @PathVariable @Positive Long couponId,
            @Valid @RequestBody AdminCouponGrantRequest request) {
        MemberCoupon granted = adminCouponService.grant(couponId, request.memberId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AdminMemberCouponResponse.from(granted));
    }
}
