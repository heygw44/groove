package com.groove.review.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.auth.jwt.JwtProvider;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.PageResponse;
import com.groove.global.config.JacksonConfig;
import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;
import com.groove.member.entity.MemberRole;
import com.groove.review.dto.ReviewCreateRequest;
import com.groove.review.dto.ReviewEligibilityResponse;
import com.groove.review.dto.ReviewIneligibleReason;
import com.groove.review.dto.ReviewResponse;
import com.groove.review.dto.ReviewStatsResponse;
import com.groove.review.dto.ReviewUpdateRequest;
import com.groove.review.service.ReviewService;

@WebMvcTest(ReviewController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
	JwtProvider.class, JacksonConfig.class})
@ActiveProfiles("test")
class ReviewControllerTest {

	private static final Long MEMBER_ID = 1L;
	private static final Long PRODUCT_ID = 10L;
	private static final Long REVIEW_ID = 100L;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	JwtProvider jwtProvider;

	@MockitoBean
	ReviewService reviewService;

	private String userToken() {
		return "Bearer " + jwtProvider.createAccessToken(MEMBER_ID, MemberRole.USER);
	}

	private ReviewResponse sampleResponse() {
		return new ReviewResponse(REVIEW_ID, PRODUCT_ID, "그루버", 5, "최고예요", "내용", LocalDateTime.now(),
				LocalDateTime.now(), true);
	}

	@Nested
	@DisplayName("GET /api/v1/products/{productId}/reviews")
	class GetReviews {

		@Test
		@DisplayName("비로그인이어도 200 과 목록을 반환한다")
		void returnsReviewsWithoutLogin() throws Exception {
			// given
			given(reviewService.getReviews(eq(PRODUCT_ID), isNull(), any()))
					.willReturn(PageResponse.of(List.of(sampleResponse()), 0, 10, 1));

			// when & then
			mockMvc.perform(get("/api/v1/products/{productId}/reviews", PRODUCT_ID))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.content[0].nickname", is("그루버")));
		}

		@Test
		@DisplayName("정의되지 않은 sort 값이면 400 COMMON_INVALID_INPUT 을 반환한다")
		void returnsBadRequestWhenSortInvalid() throws Exception {
			// when & then
			mockMvc.perform(get("/api/v1/products/{productId}/reviews", PRODUCT_ID).param("sort", "unknown"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_INVALID_INPUT")));
		}
	}

	@Nested
	@DisplayName("GET /api/v1/products/{productId}/reviews/eligibility")
	class CheckEligibility {

		@Test
		@DisplayName("비로그인이어도 200 과 LOGIN_REQUIRED 를 반환한다")
		void returnsLoginRequiredWithoutLogin() throws Exception {
			// given
			given(reviewService.checkEligibility(PRODUCT_ID, null))
					.willReturn(ReviewEligibilityResponse.deny(ReviewIneligibleReason.LOGIN_REQUIRED));

			// when & then
			mockMvc.perform(get("/api/v1/products/{productId}/reviews/eligibility", PRODUCT_ID))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.eligible", is(false)))
					.andExpect(jsonPath("$.data.reason", is("LOGIN_REQUIRED")));
		}
	}

	@Nested
	@DisplayName("GET /api/v1/products/{productId}/reviews/stats")
	class GetStats {

		@Test
		@DisplayName("비로그인이어도 200 과 별점 분포를 반환한다")
		void returnsStatsWithoutLogin() throws Exception {
			// given
			Map<Integer, Long> distribution = Map.of(1, 0L, 2, 0L, 3, 1L, 4, 0L, 5, 2L);
			given(reviewService.getStats(PRODUCT_ID))
					.willReturn(new ReviewStatsResponse(new BigDecimal("4.3"), 3, distribution));

			// when & then
			mockMvc.perform(get("/api/v1/products/{productId}/reviews/stats", PRODUCT_ID))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.distribution.5", is(2)))
					.andExpect(jsonPath("$.data.reviewCount", is(3)));
		}

		@Test
		@DisplayName("존재하지 않는 상품이면 404 를 반환한다")
		void returnsNotFoundWhenProductMissing() throws Exception {
			// given
			given(reviewService.getStats(PRODUCT_ID)).willThrow(new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

			// when & then
			mockMvc.perform(get("/api/v1/products/{productId}/reviews/stats", PRODUCT_ID))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code", is("PRODUCT_NOT_FOUND")));
		}
	}

	@Nested
	@DisplayName("POST /api/v1/products/{productId}/reviews")
	class Create {

		@Test
		@DisplayName("정상 요청이면 201 을 반환한다")
		void createsReviewWhenValid() throws Exception {
			// given
			given(reviewService.create(eq(PRODUCT_ID), eq(MEMBER_ID), any())).willReturn(sampleResponse());

			// when & then
			mockMvc.perform(post("/api/v1/products/{productId}/reviews", PRODUCT_ID)
							.header(HttpHeaders.AUTHORIZATION, userToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new ReviewCreateRequest(5, "제목", "내용"))))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.id", is(REVIEW_ID.intValue())));
		}

		@Test
		@DisplayName("rating 이 0 이면 400 을 반환한다")
		void returnsBadRequestWhenRatingBelowMin() throws Exception {
			// when & then
			mockMvc.perform(post("/api/v1/products/{productId}/reviews", PRODUCT_ID)
							.header(HttpHeaders.AUTHORIZATION, userToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new ReviewCreateRequest(0, "제목", "내용"))))
					.andExpect(status().isBadRequest());
			verify(reviewService, never()).create(any(), any(), any());
		}

		@Test
		@DisplayName("rating 이 6 이면 400 을 반환한다")
		void returnsBadRequestWhenRatingAboveMax() throws Exception {
			// when & then
			mockMvc.perform(post("/api/v1/products/{productId}/reviews", PRODUCT_ID)
							.header(HttpHeaders.AUTHORIZATION, userToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new ReviewCreateRequest(6, "제목", "내용"))))
					.andExpect(status().isBadRequest());
			verify(reviewService, never()).create(any(), any(), any());
		}

		@Test
		@DisplayName("content 가 1001자면 400 을 반환한다")
		void returnsBadRequestWhenContentTooLong() throws Exception {
			// given
			String tooLong = "a".repeat(1001);

			// when & then
			mockMvc.perform(post("/api/v1/products/{productId}/reviews", PRODUCT_ID)
							.header(HttpHeaders.AUTHORIZATION, userToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new ReviewCreateRequest(5, "제목", tooLong))))
					.andExpect(status().isBadRequest());
			verify(reviewService, never()).create(any(), any(), any());
		}

		@Test
		@DisplayName("비로그인이면 401 을 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(post("/api/v1/products/{productId}/reviews", PRODUCT_ID)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new ReviewCreateRequest(5, "제목", "내용"))))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(reviewService, never()).create(any(), any(), any());
		}
	}

	@Nested
	@DisplayName("PATCH /api/v1/reviews/{id}")
	class Update {

		@Test
		@DisplayName("정상 요청이면 200 을 반환한다")
		void updatesReviewWhenValid() throws Exception {
			// given
			given(reviewService.update(eq(REVIEW_ID), eq(MEMBER_ID), any())).willReturn(sampleResponse());

			// when & then
			mockMvc.perform(patch("/api/v1/reviews/{id}", REVIEW_ID)
							.header(HttpHeaders.AUTHORIZATION, userToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new ReviewUpdateRequest(4, "제목", "내용"))))
					.andExpect(status().isOk());
		}
	}

	@Nested
	@DisplayName("DELETE /api/v1/reviews/{id}")
	class Delete {

		@Test
		@DisplayName("정상 요청이면 200 을 반환한다")
		void deletesReviewWhenValid() throws Exception {
			// when & then
			mockMvc.perform(delete("/api/v1/reviews/{id}", REVIEW_ID)
							.header(HttpHeaders.AUTHORIZATION, userToken()))
					.andExpect(status().isOk());
			verify(reviewService).delete(REVIEW_ID, MEMBER_ID);
		}
	}
}
