/**
 * 결제 REST 진입점. {@code @Idempotent} 로 {@code Idempotency-Key} 헤더를 강제하고, 처리 본체는
 * {@code IdempotencyService} 를 통해 멱등 실행한다. 컨트롤러는 비트랜잭션 — {@code PaymentService} 가
 * 트랜잭션 경계를 갖는다.
 */
package com.groove.payment.api;
