package com.groove.claim.api;

import com.groove.auth.security.AuthPrincipal;
import com.groove.claim.api.dto.ClaimCreateRequest;
import com.groove.claim.api.dto.ClaimResponse;
import com.groove.claim.application.ClaimService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 반품 접수·조회 API (회원 전용). 본인 주문/반품 여부는 ClaimService 가 검증한다. */
@Tag(name = "반품", description = "반품 접수 · 조회 (인증 회원 전용, 본인 주문/반품만 가능)")
@RestController
@RequestMapping("/api/v1/claims")
@Validated
public class ClaimController {

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @Operation(summary = "반품 접수",
            description = "배송완료(DELIVERED 이상)된 본인 주문의 항목을 부분/전체 반품 접수한다. 반품 가능 기한(수령 후 N일) "
                    + "이내여야 하며, 이미 반품된 수량을 제외한 잔여 수량까지만 접수된다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "201", description = "반품 접수 성공")
    @ApiResponse(responseCode = "400", description = "입력 검증 실패 (항목 누락·수량 오류 등)")
    @ApiResponse(responseCode = "401", description = "인증 필요 (토큰 없음·무효·만료)")
    @ApiResponse(responseCode = "404", description = "주문을 찾을 수 없음 (본인 주문이 아닌 경우 포함)")
    @ApiResponse(responseCode = "409", description = "반품 가능 수량 초과")
    @ApiResponse(responseCode = "422", description = "반품 불가 주문 상태 · 기한 초과 · 주문에 없는 항목 · 항목 미선택")
    @PostMapping
    public ResponseEntity<ClaimResponse> create(@AuthenticationPrincipal AuthPrincipal principal,
                                                @Valid @RequestBody ClaimCreateRequest request) {
        ClaimResponse response = ClaimResponse.from(claimService.request(request.toCommand(principal.memberId())));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "반품 상세 조회",
            description = "본인이 접수한 반품의 진행 상태를 조회한다. 본인 반품이 아니면 404.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "조회 성공 — 반품 상세")
    @ApiResponse(responseCode = "400", description = "claimId 형식 오류 (양수가 아님)")
    @ApiResponse(responseCode = "401", description = "인증 필요 (토큰 없음·무효·만료)")
    @ApiResponse(responseCode = "404", description = "반품을 찾을 수 없음 (본인 반품이 아닌 경우 포함)")
    @GetMapping("/{claimId}")
    public ResponseEntity<ClaimResponse> get(@AuthenticationPrincipal AuthPrincipal principal,
                                             @Parameter(description = "반품 식별자") @PathVariable @Positive Long claimId) {
        return ResponseEntity.ok(ClaimResponse.from(claimService.findForMember(principal.memberId(), claimId)));
    }
}
