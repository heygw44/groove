package com.groove.product.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.groove.auth.jwt.JwtProvider;
import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;
import com.groove.product.service.ProductReferenceService;

@WebMvcTest(ProductReferenceController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
	JwtProvider.class})
@ActiveProfiles("test")
class ProductReferenceControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProductReferenceService productReferenceService;

	@Nested
	@DisplayName("GET /api/v1/genres")
	class GetGenres {

		@Test
		@DisplayName("비로그인 상태로도 200 과 장르 목록을 반환한다")
		void returnsGenresWithoutAuthentication() throws Exception {
			// given
			given(productReferenceService.getGenres()).willReturn(List.of());

			// when & then
			mockMvc.perform(get("/api/v1/genres"))
					.andExpect(status().isOk());
		}
	}

	@Nested
	@DisplayName("GET /api/v1/labels")
	class GetLabels {

		@Test
		@DisplayName("비로그인 상태로도 200 과 레이블 목록을 반환한다")
		void returnsLabelsWithoutAuthentication() throws Exception {
			// given
			given(productReferenceService.getLabels()).willReturn(List.of());

			// when & then
			mockMvc.perform(get("/api/v1/labels"))
					.andExpect(status().isOk());
		}
	}

	@Nested
	@DisplayName("GET /api/v1/artists")
	class SearchArtists {

		@Test
		@DisplayName("비로그인 상태로도 200 과 아티스트 목록을 반환한다")
		void returnsArtistsWithoutAuthentication() throws Exception {
			// given
			given(productReferenceService.searchArtists(any())).willReturn(List.of());

			// when & then
			mockMvc.perform(get("/api/v1/artists"))
					.andExpect(status().isOk());
		}

		@Test
		@DisplayName("keyword 파라미터를 서비스에 그대로 전달한다")
		void passesKeywordToService() throws Exception {
			// given
			given(productReferenceService.searchArtists(any())).willReturn(List.of());

			// when
			mockMvc.perform(get("/api/v1/artists").param("keyword", "miles"))
					.andExpect(status().isOk());

			// then
			verify(productReferenceService).searchArtists(eq("miles"));
		}
	}
}
