package com.groove.recommend.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.groove.auth.jwt.JwtProvider;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;
import com.groove.member.entity.MemberRole;
import com.groove.product.dto.ProductSummaryResponse;
import com.groove.product.entity.ProductStatus;
import com.groove.recommend.dto.HomeRecommendResponse;
import com.groove.recommend.dto.RecommendItemResponse;
import com.groove.recommend.dto.RecommendReason;
import com.groove.recommend.service.RecommendService;

@WebMvcTest(RecommendController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
	JwtProvider.class})
@ActiveProfiles("test")
class RecommendControllerTest {

	private static final Long MEMBER_ID = 1L;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtProvider jwtProvider;

	@MockitoBean
	private RecommendService recommendService;

	private String bearer() {
		return "Bearer " + jwtProvider.createAccessToken(MEMBER_ID, MemberRole.USER);
	}

	private static ProductSummaryResponse summary(Long id) {
		return new ProductSummaryResponse(id, "title", "artist", null, BigDecimal.ONE, null, null,
				ProductStatus.ON_SALE, null, null, 0L, null);
	}

	private static RecommendItemResponse item(Long id) {
		return new RecommendItemResponse(summary(id), List.of(RecommendReason.TASTE_ARTIST));
	}

	@Nested
	@DisplayName("GET /api/v1/recommend/home")
	class RecommendHome {

		private static final String PATH = "/api/v1/recommend/home";

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			mockMvc.perform(get(PATH))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
		}

		@Test
		@DisplayName("인증된 요청이면 200 과 추천 목록을 반환한다")
		void returnsHomeRecommendation() throws Exception {
			given(recommendService.recommendHome(MEMBER_ID, null))
					.willReturn(HomeRecommendResponse.of(List.of(item(10L))));

			mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.profileRequired", is(false)))
					.andExpect(jsonPath("$.data.items[0].product.id", is(10)))
					.andExpect(jsonPath("$.data.items[0].reasons[0]", is("TASTE_ARTIST")));
		}

		@Test
		@DisplayName("size 쿼리를 서비스에 그대로 전달한다")
		void passesSizeQueryToService() throws Exception {
			given(recommendService.recommendHome(eq(MEMBER_ID), eq(20)))
					.willReturn(HomeRecommendResponse.of(List.of()));

			mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer()).param("size", "20"))
					.andExpect(status().isOk());

			verify(recommendService).recommendHome(MEMBER_ID, 20);
		}

		@Test
		@DisplayName("서비스가 COMMON_INVALID_INPUT 을 던지면 400 을 반환한다")
		void returnsBadRequestWhenServiceRejectsSize() throws Exception {
			willThrow(new BusinessException(ErrorCode.COMMON_INVALID_INPUT))
					.given(recommendService).recommendHome(eq(MEMBER_ID), any());

			mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer()).param("size", "999"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_INVALID_INPUT")));
		}
	}

	@Nested
	@DisplayName("GET /api/v1/products/{id}/related")
	class RecommendRelated {

		private String path(Long id) {
			return "/api/v1/products/" + id + "/related";
		}

		@Test
		@DisplayName("비로그인이어도 200 을 반환하고 서비스에 memberId null 을 전달한다")
		void returnsOkAndPassesNullMemberIdWhenAnonymous() throws Exception {
			given(recommendService.recommendRelated(eq(1L), isNull(), any()))
					.willReturn(List.of(item(20L)));

			mockMvc.perform(get(path(1L)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data[0].product.id", is(20)));

			verify(recommendService).recommendRelated(1L, null, null);
		}

		@Test
		@DisplayName("토큰이 있으면 서비스에 memberId 를 전달한다")
		void passesMemberIdWhenAuthenticated() throws Exception {
			given(recommendService.recommendRelated(eq(1L), eq(MEMBER_ID), any()))
					.willReturn(List.of(item(20L)));

			mockMvc.perform(get(path(1L)).header(HttpHeaders.AUTHORIZATION, bearer()))
					.andExpect(status().isOk());

			verify(recommendService).recommendRelated(1L, MEMBER_ID, null);
		}

		@Test
		@DisplayName("기준 상품이 없으면 404 PRODUCT_NOT_FOUND 를 반환한다")
		void returnsNotFoundWhenProductMissing() throws Exception {
			willThrow(new BusinessException(ErrorCode.PRODUCT_NOT_FOUND))
					.given(recommendService).recommendRelated(eq(1L), isNull(), any());

			mockMvc.perform(get(path(1L)))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code", is("PRODUCT_NOT_FOUND")));
		}
	}
}
