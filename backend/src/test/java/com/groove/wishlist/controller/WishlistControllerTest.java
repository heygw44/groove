package com.groove.wishlist.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;
import com.groove.member.entity.MemberRole;
import com.groove.product.entity.ProductStatus;
import com.groove.wishlist.dto.WishlistAddRequest;
import com.groove.wishlist.dto.WishlistItemResponse;
import com.groove.wishlist.service.WishlistService;

@WebMvcTest(WishlistController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
	JwtProvider.class})
@ActiveProfiles("test")
class WishlistControllerTest {

	private static final String BASE_URL = "/api/v1/wishlist";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	JwtProvider jwtProvider;

	@MockitoBean
	WishlistService wishlistService;

	private String bearer() {
		return "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.USER);
	}

	private WishlistItemResponse sampleItemResponse() {
		return new WishlistItemResponse(10L, 100L, "Kind of Blue", "Miles Davis", "https://cdn.groove.com/0.jpg",
				new BigDecimal("45000"), ProductStatus.ON_SALE, 5, LocalDateTime.now());
	}

	@Nested
	@DisplayName("GET /api/v1/wishlist")
	class GetWishlist {

		@Test
		@DisplayName("인증된 요청이면 200 과 위시리스트 목록을 반환한다")
		void returnsWishlist() throws Exception {
			// given
			PageResponse<WishlistItemResponse> page = PageResponse.of(List.of(sampleItemResponse()), 0, 20, 1);
			given(wishlistService.getWishlist(eq(1L), any())).willReturn(page);

			// when & then
			mockMvc.perform(get(BASE_URL).header(HttpHeaders.AUTHORIZATION, bearer()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.content[0].productId", is(100)));
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(get(BASE_URL))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(wishlistService, never()).getWishlist(any(), any());
		}

		@Test
		@DisplayName("size 가 100 을 초과하면 400 COMMON_VALIDATION_FAILED 를 반환한다")
		void returnsBadRequestWhenSizeExceedsLimit() throws Exception {
			// when & then
			mockMvc.perform(get(BASE_URL).header(HttpHeaders.AUTHORIZATION, bearer()).param("size", "101"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")));
		}
	}

	@Nested
	@DisplayName("POST /api/v1/wishlist")
	class Add {

		@Test
		@DisplayName("유효한 요청이면 201 과 등록된 항목을 반환한다")
		void addsWishlist() throws Exception {
			// given
			given(wishlistService.add(eq(1L), any())).willReturn(sampleItemResponse());

			// when & then
			mockMvc.perform(post(BASE_URL)
							.header(HttpHeaders.AUTHORIZATION, bearer())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new WishlistAddRequest(100L))))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.productId", is(100)));
			verify(wishlistService).add(eq(1L), any());
		}

		@Test
		@DisplayName("productId 가 없으면 400 COMMON_VALIDATION_FAILED 를 반환한다")
		void returnsBadRequestWhenProductIdMissing() throws Exception {
			// when & then
			mockMvc.perform(post(BASE_URL)
							.header(HttpHeaders.AUTHORIZATION, bearer())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new WishlistAddRequest(null))))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")))
					.andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem("productId")));
		}

		@Test
		@DisplayName("이미 등록된 상품이면 409 WISHLIST_ALREADY_EXISTS 를 반환한다")
		void returnsConflictWhenAlreadyExists() throws Exception {
			// given
			willThrow(new BusinessException(ErrorCode.WISHLIST_ALREADY_EXISTS))
					.given(wishlistService).add(eq(1L), any());

			// when & then
			mockMvc.perform(post(BASE_URL)
							.header(HttpHeaders.AUTHORIZATION, bearer())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new WishlistAddRequest(100L))))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error.code", is("WISHLIST_ALREADY_EXISTS")));
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(post(BASE_URL)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new WishlistAddRequest(100L))))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(wishlistService, never()).add(any(), any());
		}
	}

	@Nested
	@DisplayName("DELETE /api/v1/wishlist/{productId}")
	class Remove {

		@Test
		@DisplayName("인증된 요청이면 200 을 반환하고 삭제를 처리한다")
		void removesWishlist() throws Exception {
			// when & then
			mockMvc.perform(delete(BASE_URL + "/100").header(HttpHeaders.AUTHORIZATION, bearer()))
					.andExpect(status().isOk());
			verify(wishlistService).remove(1L, 100L);
		}

		@Test
		@DisplayName("등록되지 않은 상품이면 404 WISHLIST_NOT_FOUND 를 반환한다")
		void returnsNotFoundWhenNotRegistered() throws Exception {
			// given
			willThrow(new BusinessException(ErrorCode.WISHLIST_NOT_FOUND))
					.given(wishlistService).remove(1L, 100L);

			// when & then
			mockMvc.perform(delete(BASE_URL + "/100").header(HttpHeaders.AUTHORIZATION, bearer()))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code", is("WISHLIST_NOT_FOUND")));
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(delete(BASE_URL + "/100"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(wishlistService, never()).remove(any(), any());
		}
	}
}
