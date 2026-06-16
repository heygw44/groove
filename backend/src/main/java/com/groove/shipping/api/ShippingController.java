package com.groove.shipping.api;

import com.groove.shipping.api.dto.ShippingResponse;
import com.groove.shipping.application.ShippingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 배송 API. GET /shippings/{trackingNumber} 는 permitAll(공개). 운송장 번호는 UUID 형식(hex + 하이픈)이며
 * 그 외 형식의 path 는 400 으로 거른다.
 */
@Tag(name = "배송", description = "운송장 번호로 배송 진행 상태 조회 (운송장 번호를 아는 사람이면 누구나 — 공개)")
@RestController
@RequestMapping("/api/v1/shippings")
@Validated
public class ShippingController {

    private static final String TRACKING_NUMBER_REGEX = "^[0-9a-fA-F-]{8,50}$";

    private final ShippingService shippingService;

    public ShippingController(ShippingService shippingService) {
        this.shippingService = shippingService;
    }

    @Operation(summary = "배송 조회",
            description = "운송장 번호(UUID 형식)로 배송 진행 상태·시각을 조회한다. 운송장 번호를 아는 사람이면 누구나 조회할 수 있다. (공개 엔드포인트)")
    @ApiResponse(responseCode = "200", description = "배송 조회 성공")
    @ApiResponse(responseCode = "400", description = "운송장 번호 형식 위반")
    @ApiResponse(responseCode = "404", description = "해당 운송장의 배송 없음 (SHIPPING_NOT_FOUND)")
    @GetMapping("/{trackingNumber}")
    public ResponseEntity<ShippingResponse> get(
            @Parameter(description = "조회할 운송장 번호 (UUID 형식)", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable @Pattern(regexp = TRACKING_NUMBER_REGEX) String trackingNumber) {
        return ResponseEntity.ok(shippingService.findByTrackingNumber(trackingNumber));
    }
}
