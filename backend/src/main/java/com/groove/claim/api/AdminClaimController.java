package com.groove.claim.api;

import com.groove.claim.api.dto.AdminClaimSummaryResponse;
import com.groove.claim.api.dto.ClaimRejectRequest;
import com.groove.claim.api.dto.ClaimResponse;
import com.groove.claim.application.ClaimService;
import com.groove.claim.domain.Claim;
import com.groove.claim.domain.ClaimStatus;
import com.groove.common.api.PageResponse;
import com.groove.common.api.SortValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * 관리자 반품 관리 API (#239).
 *
 * <p>인가 경계는 {@code SecurityConfig} 의 {@code /api/v1/admin/**} → {@code hasRole("ADMIN")} 가 담당하므로
 * 컨트롤러에 별도 권한 어노테이션을 두지 않는다({@code AdminOrderController} 동일 패턴). 승인(approve)·거부(reject)·
 * 수동 환불(complete)은 관리자 판단이며, 회수·검수·검수통과+환불은 {@code ClaimProgressScheduler} 가 자동 진행한다 —
 * complete 는 자동 환불 전에 운영자가 즉시 환불하려는 오버라이드다(INSPECTING 이 아니면 멱등 no-op).
 *
 * <p>정렬 화이트리스트: {@code createdAt} 만 허용.
 */
@Tag(name = "반품 (관리자)", description = "반품 조회·승인·거부·수동 환불 (모두 ADMIN 권한 필요)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/claims")
@Validated
public class AdminClaimController {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of("createdAt");

    private final ClaimService claimService;

    public AdminClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @Operation(summary = "반품 목록 조회",
            description = "전체 반품을 상태로 필터링해 페이지 단위로 조회한다. ADMIN 권한 필요. 정렬은 createdAt 만 허용한다.")
    @ApiResponse(responseCode = "200", description = "조회 성공 — 반품 요약 페이지")
    @ApiResponse(responseCode = "400", description = "허용되지 않은 정렬 속성")
    @ApiResponse(responseCode = "401", description = "미인증 — 로그인이 필요합니다")
    @ApiResponse(responseCode = "403", description = "권한 부족 — ADMIN 권한이 없습니다")
    @GetMapping
    public ResponseEntity<PageResponse<AdminClaimSummaryResponse>> list(
            @Parameter(description = "반품 상태 필터 — 생략 시 전체")
            @RequestParam(required = false) ClaimStatus status,
            @ParameterObject
            @PageableDefault(size = 20)
            @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        SortValidator.requireAllowed(pageable.getSort(), ALLOWED_SORT_PROPERTIES);
        Page<Claim> page = claimService.list(status, pageable);
        return ResponseEntity.ok(PageResponse.from(page, AdminClaimSummaryResponse::from));
    }

    @Operation(summary = "반품 상세 조회", description = "반품 식별자로 단건 상세를 조회한다(소유자 검증 없음). ADMIN 권한 필요.")
    @ApiResponse(responseCode = "200", description = "조회 성공 — 반품 상세")
    @ApiResponse(responseCode = "401", description = "미인증")
    @ApiResponse(responseCode = "403", description = "권한 부족")
    @ApiResponse(responseCode = "404", description = "반품을 찾을 수 없음")
    @GetMapping("/{claimId}")
    public ResponseEntity<ClaimResponse> get(
            @Parameter(description = "반품 식별자") @PathVariable @Positive Long claimId) {
        return ResponseEntity.ok(ClaimResponse.from(claimService.findDetail(claimId)));
    }

    @Operation(summary = "반품 승인",
            description = "REQUESTED 반품을 승인(APPROVED)한다. 이후 회수·검수·검수통과+환불은 스케줄러가 자동 진행한다. ADMIN 권한 필요.")
    @ApiResponse(responseCode = "200", description = "승인 성공 — 반품 상세")
    @ApiResponse(responseCode = "401", description = "미인증")
    @ApiResponse(responseCode = "403", description = "권한 부족")
    @ApiResponse(responseCode = "404", description = "반품을 찾을 수 없음")
    @ApiResponse(responseCode = "409", description = "현재 상태에서 승인 불가 (CLAIM_INVALID_STATE_TRANSITION)")
    @PostMapping("/{claimId}/approve")
    public ResponseEntity<ClaimResponse> approve(
            @Parameter(description = "반품 식별자") @PathVariable @Positive Long claimId) {
        return ResponseEntity.ok(ClaimResponse.from(claimService.approve(claimId)));
    }

    @Operation(summary = "반품 거부",
            description = "REQUESTED(접수 반려) 또는 INSPECTING(검수 불합격) 반품을 거부(REJECTED)한다. 재입고·환불은 없다. "
                    + "ADMIN 권한 필요. 사유는 선택.")
    @ApiResponse(responseCode = "200", description = "거부 성공 — 반품 상세")
    @ApiResponse(responseCode = "400", description = "사유 길이 초과")
    @ApiResponse(responseCode = "401", description = "미인증")
    @ApiResponse(responseCode = "403", description = "권한 부족")
    @ApiResponse(responseCode = "404", description = "반품을 찾을 수 없음")
    @ApiResponse(responseCode = "409", description = "현재 상태에서 거부 불가 (CLAIM_INVALID_STATE_TRANSITION)")
    @PostMapping("/{claimId}/reject")
    public ResponseEntity<ClaimResponse> reject(
            @Parameter(description = "반품 식별자") @PathVariable @Positive Long claimId,
            @Valid @RequestBody(required = false) ClaimRejectRequest request) {
        String reason = request != null ? request.reason() : null;
        return ResponseEntity.ok(ClaimResponse.from(claimService.reject(claimId, reason)));
    }

    @Operation(summary = "반품 수동 환불(검수 통과)",
            description = "INSPECTING 반품을 즉시 검수 통과 처리해 PG 환불 + 재입고 + 전량 시 쿠폰 복원을 수행한다(스케줄러 자동 "
                    + "환불의 수동 오버라이드). INSPECTING 이 아니면 부수효과 없이 현재 상태로 응답한다(멱등). ADMIN 권한 필요.")
    @ApiResponse(responseCode = "200", description = "환불 성공 또는 멱등 응답 — 반품 상세")
    @ApiResponse(responseCode = "401", description = "미인증")
    @ApiResponse(responseCode = "403", description = "권한 부족")
    @ApiResponse(responseCode = "404", description = "반품 또는 결제를 찾을 수 없음")
    @ApiResponse(responseCode = "409", description = "환불 불가 결제 상태 (PAID/PARTIALLY_REFUNDED 아님)")
    @ApiResponse(responseCode = "502", description = "PG 환불 호출 실패 (PAYMENT_GATEWAY_FAILURE)")
    @PostMapping("/{claimId}/complete")
    public ResponseEntity<ClaimResponse> complete(
            @Parameter(description = "반품 식별자") @PathVariable @Positive Long claimId) {
        return ResponseEntity.ok(ClaimResponse.from(claimService.completeRefund(claimId)));
    }
}
