/**
 * 관리자 REST 진입점 (인가 경계는 SecurityConfig 의 {@code /api/v1/admin/**} → ROLE_ADMIN). 주문·결제 등
 * 여러 도메인을 가로지르는 운영 조작을 모은다 — 단일 도메인 CRUD(앨범/아티스트 등) 는 해당 도메인 패키지에 둔다.
 */
package com.groove.admin.api;
