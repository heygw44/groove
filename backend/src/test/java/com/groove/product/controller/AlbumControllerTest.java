package com.groove.product.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;
import com.groove.member.entity.MemberRole;
import com.groove.product.dto.AlbumDetailResponse;
import com.groove.product.dto.ProductDetailResponse;
import com.groove.product.dto.ProductSummaryResponse;
import com.groove.product.entity.EditionType;
import com.groove.product.entity.ProductStatus;
import com.groove.product.service.AlbumService;

@WebMvcTest(AlbumController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
	JwtProvider.class})
@ActiveProfiles("test")
class AlbumControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtProvider jwtProvider;

	@MockitoBean
	private AlbumService albumService;

	private String bearer() {
		return "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.USER);
	}

	@Nested
	@DisplayName("GET /api/v1/albums/{id}")
	class GetDetail {

		@Test
		@DisplayName("비로그인 상태로도 200 과 앨범 상세를 반환한다")
		void returnsDetailWithoutAuthentication() throws Exception {
			// given
			ProductSummaryResponse pressing = new ProductSummaryResponse(10L, "Kind of Blue", "Miles Davis",
					"Columbia", new BigDecimal("42000"), "Standard Black", "180g", ProductStatus.ON_SALE, null, null,
					0, null, "US", 1959, EditionType.ORIGINAL);
			AlbumDetailResponse response = new AlbumDetailResponse(5L, "Kind Of Blue",
					new ProductDetailResponse.ArtistSummary(1L, "Miles Davis"), 1959, "설명", List.of(pressing), null);
			given(albumService.getDetail(eq(5L), isNull())).willReturn(response);

			// when & then
			mockMvc.perform(get("/api/v1/albums/{id}", 5L))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.id", is(5)))
					.andExpect(jsonPath("$.data.pressings[0].id", is(10)));
		}

		@Test
		@DisplayName("존재하지 않으면 404 ALBUM_NOT_FOUND 를 반환한다")
		void returnsNotFoundWhenAlbumMissing() throws Exception {
			// given
			given(albumService.getDetail(eq(99L), isNull()))
					.willThrow(new BusinessException(ErrorCode.ALBUM_NOT_FOUND));

			// when & then
			mockMvc.perform(get("/api/v1/albums/{id}", 99L))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code", is("ALBUM_NOT_FOUND")));
		}

		@Test
		@DisplayName("로그인 상태면 회원 id 를 서비스에 넘긴다")
		void passesMemberIdWhenAuthenticated() throws Exception {
			// given
			ProductSummaryResponse pressing = new ProductSummaryResponse(10L, "Kind of Blue", "Miles Davis",
					"Columbia", new BigDecimal("42000"), "Standard Black", "180g", ProductStatus.ON_SALE, null, null,
					0, null, "US", 1959, EditionType.ORIGINAL);
			AlbumDetailResponse response = new AlbumDetailResponse(5L, "Kind Of Blue",
					new ProductDetailResponse.ArtistSummary(1L, "Miles Davis"), 1959, "설명", List.of(pressing), true);
			given(albumService.getDetail(eq(5L), eq(1L))).willReturn(response);

			// when & then
			mockMvc.perform(get("/api/v1/albums/{id}", 5L).header(HttpHeaders.AUTHORIZATION, bearer()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.watched", is(true)));
		}
	}
}
