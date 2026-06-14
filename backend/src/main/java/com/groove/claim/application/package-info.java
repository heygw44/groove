/**
 * 반품 애플리케이션 서비스 (#239).
 *
 * <p>{@link com.groove.claim.application.ClaimService} 가 접수/승인/거부/환불 트랜잭션 경계를 맡고,
 * {@link com.groove.claim.application.ClaimProgressScheduler} 가 회수·검수·검수통과+환불을 시연 환경에서 자동
 * 진행한다({@code ShippingProgressScheduler} 패턴). 환불은 PG·재고·쿠폰을 단일 트랜잭션 보상 패턴으로 조율한다.
 */
package com.groove.claim.application;
