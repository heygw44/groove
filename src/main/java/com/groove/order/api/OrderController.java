package com.groove.order.api;

import com.groove.auth.security.AuthPrincipal;
import com.groove.order.api.dto.OrderCreateRequest;
import com.groove.order.api.dto.OrderResponse;
import com.groove.order.application.OrderService;
import com.groove.order.domain.Order;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 주문 API (API.md §3.5).
 *
 * <p>본 이슈(#43)는 주문 생성 단일 엔드포인트만 다룬다 — 조회/취소/관리자 기능은 후속 이슈.
 *
 * <p>회원/게스트 분기: {@code @AuthenticationPrincipal(required = false)} 로 토큰 유무를 받는다.
 * SecurityConfig 가 POST {@code /api/v1/orders} 를 permitAll 로 풀어 두므로, 토큰이 없을 때도
 * 컨트롤러까지 도달한다. 게스트 정보 누락은 {@code OrderService.place} 가 검증한다.
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
}
