/**
 * 멱등성 인프라 (#W7-2).
 *
 * <p>{@code Idempotency-Key} 기반으로 중복 호출에 안전한 연산을 1회만 처리하고 결과를 캐싱한다.
 * 결제 요청·웹훅 수신 등이 소비 대상이며, 본 패키지는 도메인에 중립적인 횡단 인프라다.
 *
 * <ul>
 *   <li>{@link com.groove.common.idempotency.IdempotencyService} — {@code execute(key, …, supplier)} 진입점.</li>
 *   <li>{@link com.groove.common.idempotency.IdempotencyRecord} — 멱등성 레코드 엔티티(마커 + 캐시).</li>
 *   <li>{@link com.groove.common.idempotency.IdempotencyRecordCleanupTask} — TTL 경과 레코드 정리.</li>
 *   <li>{@code web} 서브패키지 — {@code @Idempotent} 어노테이션 + 헤더 검증 인터셉터.</li>
 * </ul>
 */
package com.groove.common.idempotency;
