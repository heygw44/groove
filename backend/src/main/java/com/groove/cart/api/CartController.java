package com.groove.cart.api;

import com.groove.auth.security.AuthPrincipal;
import com.groove.cart.api.dto.CartItemAddRequest;
import com.groove.cart.api.dto.CartItemPatchRequest;
import com.groove.cart.api.dto.CartResponse;
import com.groove.cart.application.CartService;
import com.groove.cart.domain.Cart;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 장바구니 API (API §3.4).
 *
 * <p>인가 경계는 {@code SecurityConfig} 의 기본 정책 ({@code anyRequest().authenticated()}) 이
 * 담당한다 — {@code /api/v1/cart/**} 는 USER 토큰만 통과한다. 게스트 장바구니는 본 이슈 범위 외
 * (게스트 주문은 W6-3 에서 직접 처리).
 */
@RestController
@RequestMapping("/api/v1/cart")
@Validated
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ResponseEntity<CartResponse> get(@AuthenticationPrincipal AuthPrincipal principal) {
        Cart cart = cartService.find(principal.memberId());
        return ResponseEntity.ok(CartResponse.from(cart));
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CartItemAddRequest request) {
        Cart cart = cartService.addItem(principal.memberId(), request.albumId(), request.quantity());
        return ResponseEntity.status(HttpStatus.CREATED).body(CartResponse.from(cart));
    }

    @PatchMapping("/items/{itemId}")
    public ResponseEntity<CartResponse> changeItemQuantity(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Positive Long itemId,
            @Valid @RequestBody CartItemPatchRequest request) {
        Cart cart = cartService.changeItemQuantity(principal.memberId(), itemId, request.quantity());
        return ResponseEntity.ok(CartResponse.from(cart));
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Void> removeItem(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Positive Long itemId) {
        cartService.removeItem(principal.memberId(), itemId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clear(@AuthenticationPrincipal AuthPrincipal principal) {
        cartService.clear(principal.memberId());
        return ResponseEntity.noContent().build();
    }
}
