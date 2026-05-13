/**
 * 관리자 application 계층 — 주문 조회/상태 강제 전환/환불 트랜잭션 경계. Aggregate(order·payment) 간 조율은
 * 도메인이 아닌 이 계층 책임이라는 기존 패턴을 따른다.
 */
package com.groove.admin.application;
