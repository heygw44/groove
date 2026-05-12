/**
 * 결제 application 계층 — 결제 요청 접수 트랜잭션 경계 + 본인 결제 조회.
 *
 * <p>{@code requestPayment} 는 {@code IdempotencyService.execute} 의 {@code action} 으로 호출되는 것을
 * 전제로 자기 트랜잭션을 관리한다 — 호출자(컨트롤러)는 비트랜잭션이어야 한다.
 */
package com.groove.payment.application;
