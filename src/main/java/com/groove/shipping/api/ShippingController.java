package com.groove.shipping.api;

import com.groove.shipping.api.dto.ShippingResponse;
import com.groove.shipping.application.ShippingService;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 배송 API (API.md §3.7).
 *
 * <p>{@code GET /shippings/{trackingNumber}} 는 SecurityConfig 에서 permitAll — 운송장 번호를 아는
 * 사람(주문자)이면 누구나 조회할 수 있다. 운송장 번호는 {@code UuidTrackingNumberGenerator} 가 발급하는
 * UUID 형식(hex + 하이픈)이며, 그 외 형식의 path 는 컨트롤러 진입 단계에서 400 으로 거른다.
 */
@RestController
@RequestMapping("/api/v1/shippings")
@Validated
public class ShippingController {

    private static final String TRACKING_NUMBER_REGEX = "^[0-9a-fA-F-]{8,50}$";

    private final ShippingService shippingService;

    public ShippingController(ShippingService shippingService) {
        this.shippingService = shippingService;
    }

    @GetMapping("/{trackingNumber}")
    public ResponseEntity<ShippingResponse> get(
            @PathVariable @Pattern(regexp = TRACKING_NUMBER_REGEX) String trackingNumber) {
        return ResponseEntity.ok(shippingService.findByTrackingNumber(trackingNumber));
    }
}
