package com.groove.order.api;

import com.groove.auth.security.AuthPrincipal;
import com.groove.common.hash.Sha256Hasher;
import com.groove.common.idempotency.IdempotencyService;
import com.groove.common.idempotency.web.Idempotent;
import com.groove.common.idempotency.web.IdempotencyKeyInterceptor;
import com.groove.order.api.dto.GuestLookupRequest;
import com.groove.order.api.dto.OrderCancelRequest;
import com.groove.order.api.dto.OrderCreateRequest;
import com.groove.order.api.dto.OrderResponse;
import com.groove.order.application.OrderService;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderNumberFormat;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;

/**
 * 주문 API — 생성, 단건 조회, 취소, 게스트 lookup.
 * 회원/게스트 분기는 @AuthenticationPrincipal(required = false) 로 토큰 유무를 받는다.
 *
 * <p>POST /orders 는 Idempotency-Key 헤더를 검증(없으면 400)하고 같은 키당 한 번만 주문을 생성하며,
 * 재요청은 캐시된 응답을 replay 한다(타임아웃 후 재시도 시 중복 주문·재고 과차감 방지, #317).
 */
@Tag(name = "주문", description = "주문 생성(회원/게스트, 멱등) · 본인 주문 단건 조회 · 취소 · 게스트 본인 조회")
@RestController
@RequestMapping("/api/v1/orders")
@Validated
public class OrderController {

    private final OrderService orderService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public OrderController(OrderService orderService,
                           IdempotencyService idempotencyService,
                           ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "주문 생성",
            description = "회원/게스트 공통 주문 생성. Bearer 토큰이 있으면 회원 주문, 없으면 게스트 주문으로 처리되며 게스트는 본문의 guest 블록이 필수다. "
                    + "Idempotency-Key 헤더가 필수이며(없으면 400), 같은 키 재요청은 캐시된 응답을 그대로 replay 한다. "
                    + "성공 시 Location 헤더에 생성된 주문 리소스 URI 를 담는다. 회원 주문은 memberCouponId 로 쿠폰 1장을 적용할 수 있다. (공개 엔드포인트)")
    @ApiResponse(responseCode = "201", description = "주문 생성 성공 — 동일 키 replay 도 201")
    @ApiResponse(responseCode = "400", description = "입력 검증 실패 또는 Idempotency-Key 헤더 누락 (items 누락·수량 범위·배송지·게스트 정보 형식 · IDEMPOTENCY_KEY_REQUIRED)")
    @ApiResponse(responseCode = "404", description = "주문 항목의 앨범을 찾을 수 없음")
    @ApiResponse(responseCode = "409", description = "재고 부족·멱등 키 충돌/재사용 불일치 "
            + "(ORDER_INSUFFICIENT_STOCK · IDEMPOTENCY_IN_PROGRESS · IDEMPOTENCY_KEY_REUSE_MISMATCH)")
    @ApiResponse(responseCode = "422", description = "구매 불가 앨범·게스트에 쿠폰 동봉 등 도메인 규칙 위반")
    @Parameter(in = ParameterIn.HEADER, name = "Idempotency-Key", required = true,
            description = "멱등 키 — 같은 키 재요청은 캐시된 응답을 replay 한다",
            example = "9f1c2e6a-3b4d-4f5a-8c7e-1a2b3c4d5e6f")
    @PostMapping
    @Idempotent
    public ResponseEntity<OrderResponse> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody OrderCreateRequest request,
            HttpServletRequest httpRequest) {
        String idempotencyKey = (String) httpRequest.getAttribute(IdempotencyKeyInterceptor.KEY_ATTRIBUTE);
        Long memberId = principal != null ? principal.memberId() : null;
        String fingerprint = orderFingerprint(memberId, request);

        OrderResponse response = idempotencyService.execute(
                idempotencyKey, fingerprint, OrderResponse.class,
                () -> orderService.placeAndRespond(memberId, request));
        URI location = URI.create("/api/v1/orders/" + response.orderNumber());
        return ResponseEntity.created(location).body(response);
    }

    /**
     * 같은 멱등 키를 다른 소유자/본문으로 재사용하면 409(mismatch)로 막기 위한 요청 지문.
     * 소유자 태그(게스트 email 은 최대 255자)와 본문을 합쳐 통째로 SHA-256 해시해, raw email 길이와 무관하게
     * request_fingerprint 컬럼(255자) 한도 안에서 항상 고정 64자 hex 를 만든다.
     */
    private String orderFingerprint(Long memberId, OrderCreateRequest request) {
        String owner = memberId != null
                ? "m:" + memberId
                : "g:" + (request.guest() != null ? request.guest().email() : "");
        return Sha256Hasher.hex(owner + "|" + objectMapper.writeValueAsString(request));
    }

    @Operation(summary = "본인 주문 단건 조회",
            description = "로그인한 회원이 자신의 주문을 주문번호로 단건 조회한다. 본인 주문이 아니면 404 로 응답한다(존재 노출 회피).")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "주문 조회 성공")
    @ApiResponse(responseCode = "400", description = "주문번호 형식 위반")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "404", description = "주문 없음 또는 본인 주문 아님 (ORDER_NOT_FOUND)")
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
    @ApiResponse(responseCode = "400", description = "주문번호 형식 위반 또는 취소 사유 길이 초과")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "404", description = "주문 없음 또는 본인 주문 아님 (ORDER_NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "현재 주문 상태에서 취소 불가 (ORDER_INVALID_STATE_TRANSITION)")
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

    /** 게스트 본인 주문 조회 — orderNumber 와 email 매칭. IP 단위 Rate Limit 적용. */
    @Operation(summary = "게스트 본인 주문 조회",
            description = "비로그인 게스트가 주문번호와 주문 시 입력한 이메일을 함께 제시해 자신의 주문을 조회한다. "
                    + "이메일이 일치하지 않으면 404 로 응답한다(정보 노출 회피). (공개 엔드포인트)")
    @ApiResponse(responseCode = "200", description = "주문 조회 성공")
    @ApiResponse(responseCode = "400", description = "주문번호 형식 위반 또는 이메일 형식 오류")
    @ApiResponse(responseCode = "404", description = "주문 없음 또는 이메일 불일치 (ORDER_NOT_FOUND)")
    @PostMapping("/{orderNumber}/guest-lookup")
    public ResponseEntity<OrderResponse> guestLookup(
            @Parameter(description = "조회할 주문번호 (형식: ORD-YYYYMMDD-XXXXXX)", example = "ORD-20260101-AB12CD")
            @PathVariable @Pattern(regexp = OrderNumberFormat.PATTERN) String orderNumber,
            @Valid @RequestBody GuestLookupRequest request) {
        Order order = orderService.findForGuest(orderNumber, request.email());
        return ResponseEntity.ok(OrderResponse.from(order));
    }
}
