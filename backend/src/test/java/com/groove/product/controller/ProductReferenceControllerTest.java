package com.groove.product.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;
import com.groove.product.dto.ArtistResponse;
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

	@Nested
	@DisplayName("GET /api/v1/artists/{id}")
	class GetArtist {

		@Test
		@DisplayName("비로그인 상태로도 200 과 아티스트 정보를 반환한다")
		void returnsArtistWithoutAuthentication() throws Exception {
			// given
			given(productReferenceService.getArtist(1L))
					.willReturn(new ArtistResponse(1L, "Miles Davis", "Miles Davis"));

			// when & then
			mockMvc.perform(get("/api/v1/artists/{id}", 1L))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.id").value(1))
					.andExpect(jsonPath("$.data.name").value("Miles Davis"));
		}

		@Test
		@DisplayName("존재하지 않는 아티스트면 404 와 ARTIST_NOT_FOUND 를 반환한다")
		void returnsNotFoundWhenArtistMissing() throws Exception {
			// given
			given(productReferenceService.getArtist(1L)).willThrow(new BusinessException(ErrorCode.ARTIST_NOT_FOUND));

			// when & then
			mockMvc.perform(get("/api/v1/artists/{id}", 1L))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code").value("ARTIST_NOT_FOUND"));
		}

		@Test
		@DisplayName("id 가 숫자가 아니면 400 과 COMMON_INVALID_INPUT 을 반환한다")
		void returnsBadRequestWhenIdIsNotNumeric() throws Exception {
			// when & then
			mockMvc.perform(get("/api/v1/artists/{id}", "abc"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"));
		}
	}
}
