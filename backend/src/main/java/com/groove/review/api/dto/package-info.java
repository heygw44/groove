/**
 * 리뷰 API 요청/응답 DTO (record). 요청은 Bean Validation 으로 경계에서 검증하고
 * ({@code ReviewCreateRequest} → {@code ReviewCreateCommand}), 응답({@code ReviewResponse}) 은 작성자명을 마스킹한다.
 */
package com.groove.review.api.dto;
