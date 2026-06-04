/**
 * 주문 도메인 (W6-2 #42).
 *
 * <p>주문 모델({@link com.groove.order.domain.Order}, {@link com.groove.order.domain.OrderItem})
 * 과 상태 머신({@link com.groove.order.domain.OrderStatus}) 만 정의한다.
 * 주문 생성 API · 재고 차감 · 결제 연동은 후속 이슈 (#W6-3, W7) 범위.
 *
 * <p>Order ↔ OrderItem 은 aggregate. cascade=ALL + orphanRemoval=true 로 Order 를 통해서만
 * 변경된다 (Cart 패턴과 동일). OrderItem 은 album 의 가격/제목을 스냅샷으로 보관해
 * 사후 album 수정과 무관하게 주문 이력을 보존한다.
 */
package com.groove.order.domain;
