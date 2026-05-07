/**
 * 카탈로그 도메인 애플리케이션 서비스.
 *
 * <p>관리자 CRUD 와 공개 조회의 트랜잭션 경계를 담당한다.
 * 중복 name 은 선검사 + DB UNIQUE 이중 방어선으로 처리한다.
 */
package com.groove.catalog.application;
