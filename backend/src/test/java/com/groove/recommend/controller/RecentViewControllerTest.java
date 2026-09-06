package com.groove.recommend.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
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
import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;
import com.groove.member.entity.MemberRole;
import com.groove.product.dto.ProductSummaryResponse;
import com.groove.product.entity.ProductStatus;
import com.groove.recommend.service.RecentViewService;

@WebMvcTest(RecentViewController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
	JwtProvider.class})
@ActiveProfiles("test")
class RecentViewControllerTest {

	private static final String PATH = "/api/v1/members/me/recent-views";
	private static final Long MEMBER_ID = 1L;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtProvider jwtProvider;

	@MockitoBean
	private RecentViewService recentViewService;

	private String bearer() {
		return "Bearer " + jwtProvider.createAccessToken(MEMBER_ID, MemberRole.USER);
	}

	private ProductSummaryResponse summary(Long id, String title) {
		return new ProductSummaryResponse(id, title, "artist", "label", BigDecimal.TEN, "Black", "180g",
				ProductStatus.ON_SALE, "thumb", 4.5, 3L, true);
	}

	@Nested
	@DisplayName("GET /api/v1/members/me/recent-views")
	class GetRecentViews {

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			mockMvc.perform(get(PATH))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
		}

		@Test
		@DisplayName("인증된 요청이면 200 과 최근 조회 순서대로 상품 목록을 반환한다")
		void returnsRecentViewsInOrder() throws Exception {
			given(recentViewService.getRecentViews(MEMBER_ID))
					.willReturn(List.of(summary(3L, "Round Midnight"), summary(1L, "Kind of Blue")));

			mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data[0].id", is(3)))
					.andExpect(jsonPath("$.data[0].title", is("Round Midnight")))
					.andExpect(jsonPath("$.data[1].id", is(1)));
		}
	}
}
