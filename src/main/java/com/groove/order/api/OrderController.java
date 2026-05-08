package com.groove.order.api;

import com.groove.auth.security.AuthPrincipal;
import com.groove.order.api.dto.GuestLookupRequest;
import com.groove.order.api.dto.OrderCancelRequest;
import com.groove.order.api.dto.OrderCreateRequest;
import com.groove.order.api.dto.OrderResponse;
import com.groove.order.application.OrderService;
import com.groove.order.domain.Order;
import jakarta.validation.Valid;
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
@RestController
@RequestMapping("/api/v1/orders")
@Validated
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody OrderCreateRequest request) {
        Long memberId = principal != null ? principal.memberId() : null;
        Order order = orderService.place(memberId, request);
        URI location = URI.create("/api/v1/orders/" + order.getOrderNumber());
        return ResponseEntity.created(location).body(OrderResponse.from(order));
    }

    @GetMapping("/{orderNumber}")
    public ResponseEntity<OrderResponse> get(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String orderNumber) {
        Order order = orderService.findForMember(principal.memberId(), orderNumber);
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    @PostMapping("/{orderNumber}/cancel")
    public ResponseEntity<OrderResponse> cancel(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String orderNumber,
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
    @PostMapping("/{orderNumber}/guest-lookup")
    public ResponseEntity<OrderResponse> guestLookup(
            @PathVariable String orderNumber,
            @Valid @RequestBody GuestLookupRequest request) {
        Order order = orderService.findForGuest(orderNumber, request.email());
        return ResponseEntity.ok(OrderResponse.from(order));
    }
}
