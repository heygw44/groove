package com.groove.admin.api;

import com.groove.admin.api.dto.AdminOrderResponse;
import com.groove.admin.api.dto.AdminOrderStatusChangeRequest;
import com.groove.admin.api.dto.AdminOrderSummaryResponse;
import com.groove.admin.api.dto.AdminRefundRequest;
import com.groove.admin.api.dto.AdminRefundResponse;
import com.groove.admin.application.AdminOrderSearchCriteria;
import com.groove.admin.application.AdminOrderService;
import com.groove.common.api.PageResponse;
import com.groove.common.exception.ErrorCode;
import com.groove.common.exception.ValidationException;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
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
 * 관리자 주문 조회 / 상태 강제 전환 / 환불 API (이슈 #69, PRD §5.3·§6.9, G2 게이트 "관리자 상품·주문 조작").
 *
 * <p>인가 경계는 {@code SecurityConfig} 의 {@code /api/v1/admin/**} → {@code hasRole("ADMIN")} 가 담당하므로
 * 컨트롤러에 별도 권한 어노테이션을 두지 않는다 ({@code AlbumAdminController} 와 동일 패턴) — 비관리자 토큰은
 * 403, 미인증은 401.
 *
 * <p>정렬 화이트리스트: {@code createdAt} 만 허용 — 인덱스 없는 컬럼 정렬 차단 (회원 주문 목록과 동일한 보안 패턴).
 */
@RestController
@RequestMapping("/api/v1/admin/orders")
@Validated
public class AdminOrderController {

    /** {@code RandomOrderNumberGenerator} 발급 형식 {@code ORD-YYYYMMDD-XXXXXX} — 위반 path 는 진입 단계에서 400. */
    private static final String ORDER_NUMBER_REGEX = "^ORD-\\d{8}-[A-Z0-9]{6}$";
    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of("createdAt");

    private final AdminOrderService adminOrderService;

    public AdminOrderController(AdminOrderService adminOrderService) {
        this.adminOrderService = adminOrderService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<AdminOrderSummaryResponse>> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @Positive Long memberId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20)
            @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        validateSort(pageable.getSort());
        Page<Order> page = adminOrderService.list(
                new AdminOrderSearchCriteria(status, memberId, from, to), pageable);
        return ResponseEntity.ok(PageResponse.from(page, AdminOrderSummaryResponse::from));
    }

    @GetMapping("/{orderNumber}")
    public ResponseEntity<AdminOrderResponse> get(
            @PathVariable @Pattern(regexp = ORDER_NUMBER_REGEX) String orderNumber) {
        return ResponseEntity.ok(AdminOrderResponse.from(adminOrderService.findDetail(orderNumber)));
    }

    @PatchMapping("/{orderNumber}/status")
    public ResponseEntity<AdminOrderResponse> changeStatus(
            @PathVariable @Pattern(regexp = ORDER_NUMBER_REGEX) String orderNumber,
            @Valid @RequestBody AdminOrderStatusChangeRequest request) {
        Order order = adminOrderService.changeStatus(orderNumber, request.target(), request.reason());
        return ResponseEntity.ok(AdminOrderResponse.from(order));
    }

    @PostMapping("/{orderNumber}/refund")
    public ResponseEntity<AdminRefundResponse> refund(
            @PathVariable @Pattern(regexp = ORDER_NUMBER_REGEX) String orderNumber,
            @Valid @RequestBody(required = false) AdminRefundRequest request) {
        String reason = request != null ? request.reason() : null;
        return ResponseEntity.ok(AdminRefundResponse.from(adminOrderService.refund(orderNumber, reason)));
    }

    private void validateSort(Sort sort) {
        for (Sort.Order order : sort) {
            if (!ALLOWED_SORT_PROPERTIES.contains(order.getProperty())) {
                throw new ValidationException(
                        ErrorCode.VALIDATION_FAILED,
                        "허용되지 않는 정렬 키: " + order.getProperty());
            }
        }
    }
}
