package com.groove.admin.api;

import com.groove.admin.api.dto.AdminOrderResponse;
import com.groove.admin.api.dto.AdminOrderStatusChangeRequest;
import com.groove.admin.api.dto.AdminOrderSummaryResponse;
import com.groove.admin.api.dto.AdminRefundRequest;
import com.groove.admin.api.dto.AdminRefundResponse;
import com.groove.admin.application.AdminOrderSearchCriteria;
import com.groove.admin.application.AdminOrderService;
import com.groove.common.api.PageResponse;
import com.groove.common.api.SortValidator;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderNumberFormat;
import com.groove.order.domain.OrderStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.Instant;
import java.util.Set;

/**
 * 관리자 주문 조회 / 상태 강제 전환 / 환불 API. 인가 경계는 SecurityConfig 의 관리자 경로 → ROLE_ADMIN 이
 * 담당한다(비관리자 403, 미인증 401). 정렬 화이트리스트: createdAt 만 허용.
 */
@Tag(name = "주문 (관리자)", description = "주문 조회·상태 강제 전환·환불 (모두 ADMIN 권한 필요)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/orders")
@Validated
public class AdminOrderController {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of("createdAt");

    private final AdminOrderService adminOrderService;

    public AdminOrderController(AdminOrderService adminOrderService) {
        this.adminOrderService = adminOrderService;
    }

    @Operation(summary = "주문 목록 조회",
            description = "전체 주문을 상태·회원·기간으로 필터링해 페이지 단위로 조회한다. ADMIN 권한 필요. "
                    + "정렬은 createdAt 만 허용한다.")
    @ApiResponse(responseCode = "200", description = "조회 성공 — 주문 요약 페이지")
    @ApiResponse(responseCode = "400", description = "허용되지 않은 정렬 속성·잘못된 기간 형식 등")
    @ApiResponse(responseCode = "401", description = "미인증 — 로그인이 필요합니다")
    @ApiResponse(responseCode = "403", description = "권한 부족 — ADMIN 권한이 없습니다")
    @GetMapping
    public ResponseEntity<PageResponse<AdminOrderSummaryResponse>> list(
            @Parameter(description = "주문 상태 필터 — 생략 시 전체")
            @RequestParam(required = false) OrderStatus status,
            @Parameter(description = "회원 ID 필터 — 생략 시 전체", example = "42")
            @RequestParam(required = false) @Positive Long memberId,
            @Parameter(description = "생성 시각 시작 경계(이상, ISO-8601 UTC)", example = "2026-06-01T00:00:00Z")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "생성 시각 종료 경계(미만, ISO-8601 UTC)", example = "2026-07-01T00:00:00Z")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @ParameterObject
            @PageableDefault(size = 20)
            @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        SortValidator.requireAllowed(pageable.getSort(), ALLOWED_SORT_PROPERTIES);
        Page<Order> page = adminOrderService.list(
                new AdminOrderSearchCriteria(status, memberId, from, to), pageable);
        return ResponseEntity.ok(PageResponse.from(page, AdminOrderSummaryResponse::from));
    }

    @Operation(summary = "주문 상세 조회",
            description = "주문 번호로 단건 상세를 조회한다(회원·게스트 주문 모두, 소유자 검증 없음). ADMIN 권한 필요.")
    @ApiResponse(responseCode = "200", description = "조회 성공 — 주문 상세")
    @ApiResponse(responseCode = "400", description = "주문 번호 형식 오류")
    @ApiResponse(responseCode = "401", description = "미인증 — 로그인이 필요합니다")
    @ApiResponse(responseCode = "403", description = "권한 부족 — ADMIN 권한이 없습니다")
    @ApiResponse(responseCode = "404", description = "주문을 찾을 수 없음")
    @GetMapping("/{orderNumber}")
    public ResponseEntity<AdminOrderResponse> get(
            @Parameter(description = "주문 번호 (ORD-YYYYMMDD-XXXXXX)", example = "ORD-20260606-A1B2C3")
            @PathVariable @Pattern(regexp = OrderNumberFormat.PATTERN) String orderNumber) {
        return ResponseEntity.ok(AdminOrderResponse.from(adminOrderService.findDetail(orderNumber)));
    }

    @Operation(summary = "주문 상태 강제 전환",
            description = "주문 상태를 운영 권한으로 강제 전환한다. ADMIN 권한 필요. 재고/결제 부수효과가 없는 전진 전이"
                    + "(PREPARING/SHIPPED/DELIVERED/COMPLETED)만 허용한다 — 취소·환불은 환불 API 를 사용한다. 사유는 필수.")
    @ApiResponse(responseCode = "200", description = "상태 전환 성공 — 변경된 주문 상세")
    @ApiResponse(responseCode = "400", description = "주문 번호 형식 오류·사유 누락·잘못된 상태값")
    @ApiResponse(responseCode = "401", description = "미인증 — 로그인이 필요합니다")
    @ApiResponse(responseCode = "403", description = "권한 부족 — ADMIN 권한이 없습니다")
    @ApiResponse(responseCode = "404", description = "주문을 찾을 수 없음")
    @ApiResponse(responseCode = "409", description = "현재 상태에서 불법 전이 (ORDER_INVALID_STATE_TRANSITION)")
    @ApiResponse(responseCode = "422", description = "지원하지 않는 강제 전환 대상 (취소·결제완료·결제실패 — DOMAIN_RULE_VIOLATION)")
    @PatchMapping("/{orderNumber}/status")
    public ResponseEntity<AdminOrderResponse> changeStatus(
            @Parameter(description = "주문 번호 (ORD-YYYYMMDD-XXXXXX)", example = "ORD-20260606-A1B2C3")
            @PathVariable @Pattern(regexp = OrderNumberFormat.PATTERN) String orderNumber,
            @Valid @RequestBody AdminOrderStatusChangeRequest request) {
        Order order = adminOrderService.changeStatus(orderNumber, request.target(), request.reason());
        return ResponseEntity.ok(AdminOrderResponse.from(order));
    }

    @Operation(summary = "주문 환불",
            description = "PG 환불 + 결제 REFUNDED 전이 + 주문 CANCELLED 전이 + 재고/쿠폰 복원을 단일 트랜잭션으로 수행한다. "
                    + "ADMIN 권한 필요. 요청 본문(사유)은 생략 가능하다. 이미 환불된 결제에 재요청하면 부수효과 없이 멱등 응답한다.")
    @ApiResponse(responseCode = "200", description = "환불 성공 (또는 이미 환불됨 — 멱등 응답)")
    @ApiResponse(responseCode = "400", description = "주문 번호 형식 오류·사유 길이 초과")
    @ApiResponse(responseCode = "401", description = "미인증 — 로그인이 필요합니다")
    @ApiResponse(responseCode = "403", description = "권한 부족 — ADMIN 권한이 없습니다")
    @ApiResponse(responseCode = "404", description = "주문 또는 결제를 찾을 수 없음")
    @ApiResponse(responseCode = "409", description = "환불 불가 결제 상태(PAID 아님) 또는 주문이 CANCELLED 로 전이 불가 (SHIPPED 이후 등)")
    @ApiResponse(responseCode = "502", description = "PG 환불 호출 실패 (PAYMENT_GATEWAY_FAILURE)")
    @PostMapping("/{orderNumber}/refund")
    public ResponseEntity<AdminRefundResponse> refund(
            @Parameter(description = "주문 번호 (ORD-YYYYMMDD-XXXXXX)", example = "ORD-20260606-A1B2C3")
            @PathVariable @Pattern(regexp = OrderNumberFormat.PATTERN) String orderNumber,
            @Valid @RequestBody(required = false) AdminRefundRequest request) {
        String reason = request != null ? request.reason() : null;
        return ResponseEntity.ok(AdminRefundResponse.from(adminOrderService.refund(orderNumber, reason)));
    }

}
