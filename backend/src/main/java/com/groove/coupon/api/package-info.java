/**
 * 쿠폰 REST 진입점 — 발급 가능 목록(Public)·선착순 발급(@Idempotent, USER)·내 쿠폰 목록.
 * 발급은 PaymentController 와 동일한 멱등 패턴(컨트롤러 비트랜잭션)을 따른다.
 */
package com.groove.coupon.api;
