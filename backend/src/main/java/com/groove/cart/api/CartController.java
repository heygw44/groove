package com.groove.cart.api;

import com.groove.auth.security.AuthPrincipal;
import com.groove.cart.api.dto.CartItemAddRequest;
import com.groove.cart.api.dto.CartItemPatchRequest;
import com.groove.cart.api.dto.CartResponse;
import com.groove.cart.application.CartService;
import com.groove.cart.domain.Cart;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 장바구니 API. /api/v1/cart/** 는 인증된 USER 토큰만 통과한다.
 */
@Tag(name = "장바구니", description = "로그인한 회원의 장바구니 조회·항목 추가/수정/삭제·비우기 (모두 인증 필요)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/cart")
@Validated
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @Operation(summary = "장바구니 조회",
            description = "본인 장바구니의 전체 항목과 합계를 조회한다. 장바구니가 없으면 빈 장바구니로 응답하며 영속화는 하지 않는다. "
                    + "각 항목의 available 은 응답 시점 재계산(판매중 && 재고충분)이다.")
    @ApiResponse(responseCode = "200", description = "장바구니 조회 성공")
    @ApiResponse(responseCode = "401", description = "인증 실패 (토큰 미제공·만료·무효)")
    @GetMapping
    public ResponseEntity<CartResponse> get(@AuthenticationPrincipal AuthPrincipal principal) {
        Cart cart = cartService.find(principal.memberId());
        return ResponseEntity.ok(CartResponse.from(cart));
    }

    @Operation(summary = "장바구니 항목 추가",
            description = "앨범을 장바구니에 담는다. 동일 앨범이 이미 담겨 있으면 수량을 누적한다. 성공 시 201 과 갱신된 장바구니 전체를 반환한다.")
    @ApiResponse(responseCode = "201", description = "항목 추가 성공 — 갱신된 장바구니 반환")
    @ApiResponse(responseCode = "400", description = "입력 검증 실패 (albumId 누락, 수량 범위 위반 등)")
    @ApiResponse(responseCode = "401", description = "인증 실패 (토큰 미제공·만료·무효)")
    @ApiResponse(responseCode = "404", description = "해당 앨범 없음")
    @ApiResponse(responseCode = "422", description = "판매 중이 아닌 앨범 · 누적 수량 상한 초과")
    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CartItemAddRequest request) {
        Cart cart = cartService.addItem(principal.memberId(), request.albumId(), request.quantity());
        return ResponseEntity.status(HttpStatus.CREATED).body(CartResponse.from(cart));
    }

    @Operation(summary = "장바구니 항목 수량 변경",
            description = "지정한 항목의 수량을 절대값으로 교체한다. 성공 시 갱신된 장바구니 전체를 반환한다.")
    @ApiResponse(responseCode = "200", description = "수량 변경 성공 — 갱신된 장바구니 반환")
    @ApiResponse(responseCode = "400", description = "입력 검증 실패 (itemId 가 양수 아님, 수량 범위 위반 등)")
    @ApiResponse(responseCode = "401", description = "인증 실패 (토큰 미제공·만료·무효)")
    @ApiResponse(responseCode = "404", description = "해당 장바구니 항목 없음")
    @ApiResponse(responseCode = "422", description = "수량 상한 초과")
    @PatchMapping("/items/{itemId}")
    public ResponseEntity<CartResponse> changeItemQuantity(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Parameter(description = "수량을 변경할 장바구니 항목 ID", example = "1") @PathVariable @Positive Long itemId,
            @Valid @RequestBody CartItemPatchRequest request) {
        Cart cart = cartService.changeItemQuantity(principal.memberId(), itemId, request.quantity());
        return ResponseEntity.ok(CartResponse.from(cart));
    }

    @Operation(summary = "장바구니 항목 삭제",
            description = "지정한 항목을 장바구니에서 제거한다. 성공 시 본문 없이 204.")
    @ApiResponse(responseCode = "204", description = "항목 삭제 성공")
    @ApiResponse(responseCode = "400", description = "itemId 가 양수가 아님")
    @ApiResponse(responseCode = "401", description = "인증 실패 (토큰 미제공·만료·무효)")
    @ApiResponse(responseCode = "404", description = "해당 장바구니 항목 없음")
    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Void> removeItem(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Parameter(description = "삭제할 장바구니 항목 ID", example = "1") @PathVariable @Positive Long itemId) {
        cartService.removeItem(principal.memberId(), itemId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "장바구니 비우기",
            description = "본인 장바구니의 모든 항목을 제거한다. 성공 시 본문 없이 204.")
    @ApiResponse(responseCode = "204", description = "장바구니 비우기 성공")
    @ApiResponse(responseCode = "401", description = "인증 실패 (토큰 미제공·만료·무효)")
    @DeleteMapping
    public ResponseEntity<Void> clear(@AuthenticationPrincipal AuthPrincipal principal) {
        cartService.clear(principal.memberId());
        return ResponseEntity.noContent().build();
    }
}
