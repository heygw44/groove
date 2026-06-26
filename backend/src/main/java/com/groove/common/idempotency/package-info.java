/**
 * 멱등성 인프라. Idempotency-Key 기반으로 중복 호출 연산을 1회만 처리하고 결과를 캐싱한다.
 *
 * - IdempotencyService — execute(key, …, supplier) 진입점.
 * - IdempotencyRecord — 멱등성 레코드 엔티티(마커 + 캐시).
 * - IdempotencyRecordCleanupTask — TTL 경과 레코드 정리.
 * - web 서브패키지 — @Idempotent 어노테이션 + 헤더 검증 인터셉터.
 */
package com.groove.common.idempotency;
