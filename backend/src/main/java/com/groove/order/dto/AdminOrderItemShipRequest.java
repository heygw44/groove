package com.groove.order.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 항목마다 다른 주문에 속할 수 있어 택배사·송장을 상품주문 단위로 받는다(공용 값 하나로 받으면 서로 다른 주문을
 * 일괄 발송할 때 같은 송장이 잘못 붙는다). courierCode 는 {@code CourierCode} 이름 문자열로 받는다 - 아예 enum
 * 타입으로 바인딩하면 JSON 파싱 단계에서 실패해 COMMON_INVALID_INPUT 이 되므로, 검증 실패를
 * COMMON_VALIDATION_FAILED 로 통일하려고 문자열로 받아 서비스에서 직접 변환한다.
 */
public record AdminOrderItemShipRequest(
		@NotEmpty @Valid List<ShipItem> items
) {

	@AssertTrue(message = "orderItemId 가 중복되었습니다.")
	public boolean isDistinctOrderItemIds() {
		if (items == null) {
			return true;
		}
		long distinctCount = items.stream().map(ShipItem::orderItemId).distinct().count();
		return distinctCount == items.size();
	}

	public record ShipItem(
			@NotNull Long orderItemId,
			@NotBlank String courierCode,
			@NotBlank @Size(max = 50) String trackingNumber
	) {
	}
}
