package com.groove.order.api;

import com.groove.auth.security.AuthPrincipal;
import com.groove.common.api.PageResponse;
import com.groove.common.api.SortValidator;
import com.groove.order.api.dto.OrderSummaryResponse;
import com.groove.order.application.OrderService;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * 회원 본인 주문 목록 (#44, API.md §3.5 — GET /members/me/orders).
 *
 * <p>{@link OrderController} 와 분리한 이유: URL prefix 가 {@code /members/me/orders} 로 다르고,
 * 본 컨트롤러는 항상 인증된 회원에게만 노출된다 — SecurityConfig 의 {@code anyRequest().authenticated()}
 * 기본 정책을 그대로 따라간다.
 *
 * <p>정렬 화이트리스트: {@code createdAt} 만 허용 — Album 검색과 동일한 보안 패턴 (인덱스 없는
 * 컬럼 정렬 차단).
 */
@RestController
@RequestMapping("/api/v1/members/me/orders")
public class MemberOrderController {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of("createdAt");

    private final OrderService orderService;

    public MemberOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<OrderSummaryResponse>> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20)
            @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        SortValidator.requireAllowed(pageable.getSort(), ALLOWED_SORT_PROPERTIES);

        Page<Order> page = orderService.listForMember(principal.memberId(), status, pageable);
        return ResponseEntity.ok(PageResponse.from(page, OrderSummaryResponse::from));
    }
}
