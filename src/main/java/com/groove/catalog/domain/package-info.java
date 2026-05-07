/**
 * 카탈로그 도메인 엔티티 / 영속성 인터페이스.
 *
 * <p>Genre, Label 은 단순 정적 카탈로그로 name UNIQUE 제약만 가진다.
 * 상태 전이·소프트 삭제 등 복잡한 도메인 정책이 없으며, 변경 가능한 필드도 name 한 개다.
 */
package com.groove.catalog.domain;
