package com.groove.order.api;

import com.groove.auth.security.AuthPrincipal;
import com.groove.order.api.dto.GuestLookupRequest;
import com.groove.order.api.dto.OrderCancelRequest;
import com.groove.order.api.dto.OrderCreateRequest;
import com.groove.order.api.dto.OrderResponse;
import com.groove.order.application.OrderService;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderNumberFormat;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 주문 API (API.md §3.5).
 *
 * <p>이슈 #43 에서 생성, 이슈 #44 에서 단건 조회 + 취소 + 게스트 lookup 추가.
 *
 * <p>회원/게스트 분기: {@code @AuthenticationPrincipal(required = false)} 로 토큰 유무를 받는다.
 * SecurityConfig 가 POST {@code /api/v1/orders} 와 게스트 lookup 경로를 permitAll 로 풀어 두며,
 * 단건 GET 과 cancel 은 {@code authenticated()} 로 보호된다.
 */
@Tag(name = "주문", description = "주문 생성(회원/게스트) · 본인 주문 단건 조회 · 취소 · 게스트 본인 조회")
@RestController
@RequestMapping("/api/v1/orders")
@Validated
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @Operation(summary = "주문 생성",
            description = "회원/게스트 공통 주문 생성. Bearer 토큰이 있으면 회원 주문, 없으면 게스트 주문으로 처리되며 게스트는 본문의 guest 블록이 필수다. "
                    + "성공 시 Location 헤더에 생성된 주문 리소스 URI 를 담는다. 회원 주문은 memberCouponId 로 쿠폰 1장을 적용할 수 있다. (공개 엔드포인트)")
    @ApiResponse(responseCode = "201", description = "주문 생성 성공")
    @ApiResponse(responseCode = "400", description = "입력 검증 실패 (items 누락·수량 범위·배송지·게스트 정보 형식 등)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "주문 항목의 앨범을 찾을 수 없음",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "재고 부족 (ORDER_INSUFFICIENT_STOCK)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "구매 불가 앨범·게스트에 쿠폰 동봉 등 도메인 규칙 위반",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody OrderCreateRequest request) {
        Long memberId = principal != null ? principal.memberId() : null;
        Order order = orderService.place(memberId, request);
        URI location = URI.create("/api/v1/orders/" + order.getOrderNumber());
        return ResponseEntity.created(location).body(OrderResponse.from(order));
    }

    @Operation(summary = "본인 주문 단건 조회",
            description = "로그인한 회원이 자신의 주문을 주문번호로 단건 조회한다. 본인 주문이 아니면 404 로 응답한다(존재 노출 회피).")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "주문 조회 성공")
    @ApiResponse(responseCode = "400", description = "주문번호 형식 위반",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "인증 필요",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "주문 없음 또는 본인 주문 아님 (ORDER_NOT_FOUND)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{orderNumber}")
    public ResponseEntity<OrderResponse> get(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Parameter(description = "조회할 주문번호 (형식: ORD-YYYYMMDD-XXXXXX)", example = "ORD-20260101-AB12CD")
            @PathVariable @Pattern(regexp = OrderNumberFormat.PATTERN) String orderNumber) {
        Order order = orderService.findForMember(principal.memberId(), orderNumber);
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    @Operation(summary = "본인 주문 취소",
            description = "로그인한 회원이 자신의 주문을 취소한다. 취소 사유(reason)는 선택이며 본문 자체도 생략 가능하다. "
                    + "현재 상태에서 취소할 수 없는 주문은 409 로 거부된다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "주문 취소 성공")
    @ApiResponse(responseCode = "400", description = "주문번호 형식 위반 또는 취소 사유 길이 초과",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "인증 필요",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "주문 없음 또는 본인 주문 아님 (ORDER_NOT_FOUND)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "현재 주문 상태에서 취소 불가 (ORDER_INVALID_STATE_TRANSITION)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/{orderNumber}/cancel")
    public ResponseEntity<OrderResponse> cancel(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Parameter(description = "취소할 주문번호 (형식: ORD-YYYYMMDD-XXXXXX)", example = "ORD-20260101-AB12CD")
            @PathVariable @Pattern(regexp = OrderNumberFormat.PATTERN) String orderNumber,
            @Valid @RequestBody(required = false) OrderCancelRequest request) {
        String reason = request != null ? request.reason() : null;
        Order order = orderService.cancel(principal.memberId(), orderNumber, reason);
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    /**
     * 게스트 본인 주문 조회 — orderNumber 와 email 매칭.
     *
     * <p>TODO(W10): orderNumber + email 페어 무차별 대입 방지 위해 IP/email 기준 rate limit 도입
     * (API.md §3.5 명시). 본 이슈(#44) 범위에서는 게스트 lookup 만 구현하고 보안 강화는 후속 처리.
     */
    @Operation(summary = "게스트 본인 주문 조회",
            description = "비로그인 게스트가 주문번호와 주문 시 입력한 이메일을 함께 제시해 자신의 주문을 조회한다. "
                    + "이메일이 일치하지 않으면 404 로 응답한다(정보 노출 회피). (공개 엔드포인트)")
    @ApiResponse(responseCode = "200", description = "주문 조회 성공")
    @ApiResponse(responseCode = "400", description = "주문번호 형식 위반 또는 이메일 형식 오류",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "주문 없음 또는 이메일 불일치 (ORDER_NOT_FOUND)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/{orderNumber}/guest-lookup")
    public ResponseEntity<OrderResponse> guestLookup(
            @Parameter(description = "조회할 주문번호 (형식: ORD-YYYYMMDD-XXXXXX)", example = "ORD-20260101-AB12CD")
            @PathVariable @Pattern(regexp = OrderNumberFormat.PATTERN) String orderNumber,
            @Valid @RequestBody GuestLookupRequest request) {
        Order order = orderService.findForGuest(orderNumber, request.email());
        return ResponseEntity.ok(OrderResponse.from(order));
    }
}
