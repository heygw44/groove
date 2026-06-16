/**
 * 반품 애플리케이션 서비스. ClaimService 가 접수/승인/거부/환불 트랜잭션 경계를 맡고,
 * ClaimProgressScheduler 가 회수·검수·검수통과+환불을 자동 진행한다.
 */
package com.groove.claim.application;
