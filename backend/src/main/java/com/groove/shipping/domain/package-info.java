/**
 * 배송 도메인 모델 (W7).
 *
 * <p>{@link com.groove.shipping.domain.Shipping} 엔티티 + 상태 enum
 * ({@link com.groove.shipping.domain.ShippingStatus}) + {@link com.groove.shipping.domain.ShippingRepository}.
 * 주문당 배송 1건({@code order_id} UNIQUE), 배송지는 주문 시점 스냅샷({@code OrderShippingInfo}) 복사,
 * 상태 전이(PREPARING→SHIPPED→DELIVERED)는 자동 진행 스케줄러가 한 단계씩 밀어준다.
 */
package com.groove.shipping.domain;
