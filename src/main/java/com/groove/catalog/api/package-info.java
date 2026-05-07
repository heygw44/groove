/**
 * 카탈로그 도메인 HTTP 어댑터.
 *
 * <p>관리자(`/api/v1/admin/genres`, `/api/v1/admin/labels`) 와 공개(`/api/v1/genres`, `/api/v1/labels`)
 * 컨트롤러를 분리해 인가 경계를 URL 패턴으로 명확히 한다.
 */
package com.groove.catalog.api;
