package com.groove.cart.controller;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import com.groove.cart.dto.CartItemAddRequest;
import com.groove.cart.dto.CartItemQuantityUpdateRequest;
import com.groove.cart.dto.CartItemResponse;
import com.groove.cart.dto.CartResponse;
import com.groove.cart.service.CartService;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;
import com.groove.member.entity.MemberRole;
import com.groove.product.entity.ProductStatus;

@WebMvcTest(CartController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
	JwtProvider.class})
@ActiveProfiles("test")
class CartControllerTest {

	private static final String BASE_URL = "/api/v1/cart";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	JwtProvider jwtProvider;

	@MockitoBean
	CartService cartService;

	private String bearer() {
		return "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.USER);
	}

	private CartItemResponse sampleItemResponse() {
		return new CartItemResponse(10L, 100L, "Kind of Blue", "Miles Davis", "https://cdn.groove.com/0.jpg",
				new BigDecimal("45000"), ProductStatus.ON_SALE, 5, 2, new BigDecimal("90000"));
	}

	private CartResponse sampleCartResponse() {
		return new CartResponse(1L, List.of(sampleItemResponse()), new BigDecimal("90000"));
	}

	@Nested
	@DisplayName("GET /api/v1/cart")
	class GetCart {

		@Test
		@DisplayName("인증된 요청이면 200 과 장바구니를 반환한다")
		void returnsCart() throws Exception {
			// given
			given(cartService.getCart(1L)).willReturn(sampleCartResponse());

			// when & then
			mockMvc.perform(get(BASE_URL).header(HttpHeaders.AUTHORIZATION, bearer()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.cartId", is(1)))
					.andExpect(jsonPath("$.data.items[0].id", is(10)));
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(get(BASE_URL))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(cartService, never()).getCart(any());
		}
	}

	@Nested
	@DisplayName("POST /api/v1/cart/items")
	class AddItem {

		@Test
		@DisplayName("유효한 요청이면 201 과 담긴 항목을 반환한다")
		void addsItem() throws Exception {
			// given
			given(cartService.addItem(eq(1L), any())).willReturn(sampleItemResponse());

			// when & then
			mockMvc.perform(post(BASE_URL + "/items")
							.header(HttpHeaders.AUTHORIZATION, bearer())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new CartItemAddRequest(100L, 2))))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.id", is(10)));
			verify(cartService).addItem(eq(1L), any());
		}

		@ParameterizedTest
		@DisplayName("수량이 1~10 범위를 벗어나면 400 COMMON_VALIDATION_FAILED 를 반환한다")
		@ValueSource(ints = {0, 11})
		void returnsBadRequestWhenQuantityOutOfRange(int quantity) throws Exception {
			// when & then
			mockMvc.perform(post(BASE_URL + "/items")
							.header(HttpHeaders.AUTHORIZATION, bearer())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new CartItemAddRequest(100L, quantity))))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")))
					.andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem("quantity")));
		}

		@Test
		@DisplayName("숨김 상품이면 404 PRODUCT_HIDDEN 을 반환한다")
		void returnsNotFoundWhenProductHidden() throws Exception {
			// given
			willThrow(new BusinessException(ErrorCode.PRODUCT_HIDDEN))
					.given(cartService).addItem(eq(1L), any());

			// when & then
			mockMvc.perform(post(BASE_URL + "/items")
							.header(HttpHeaders.AUTHORIZATION, bearer())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new CartItemAddRequest(100L, 1))))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code", is("PRODUCT_HIDDEN")));
		}
	}

	@Nested
	@DisplayName("PATCH /api/v1/cart/items/{cartItemId}")
	class UpdateQuantity {

		@Test
		@DisplayName("유효한 요청이면 200 과 변경된 항목을 반환한다")
		void updatesQuantity() throws Exception {
			// given
			given(cartService.updateQuantity(eq(1L), eq(10L), any())).willReturn(sampleItemResponse());

			// when & then
			mockMvc.perform(patch(BASE_URL + "/items/10")
							.header(HttpHeaders.AUTHORIZATION, bearer())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new CartItemQuantityUpdateRequest(3))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.id", is(10)));
		}

		@ParameterizedTest
		@DisplayName("수량이 1~10 범위를 벗어나면 400 COMMON_VALIDATION_FAILED 를 반환한다")
		@ValueSource(ints = {0, 11})
		void returnsBadRequestWhenQuantityOutOfRange(int quantity) throws Exception {
			// when & then
			mockMvc.perform(patch(BASE_URL + "/items/10")
							.header(HttpHeaders.AUTHORIZATION, bearer())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new CartItemQuantityUpdateRequest(quantity))))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")))
					.andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem("quantity")));
		}

		@Test
		@DisplayName("본인 소유가 아니면 404 CART_ITEM_NOT_FOUND 를 반환한다")
		void returnsNotFoundWhenNotOwned() throws Exception {
			// given
			willThrow(new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND))
					.given(cartService).updateQuantity(eq(1L), eq(10L), any());

			// when & then
			mockMvc.perform(patch(BASE_URL + "/items/10")
							.header(HttpHeaders.AUTHORIZATION, bearer())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new CartItemQuantityUpdateRequest(3))))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code", is("CART_ITEM_NOT_FOUND")));
		}
	}

	@Nested
	@DisplayName("DELETE /api/v1/cart/items/{cartItemId}")
	class RemoveItem {

		@Test
		@DisplayName("인증된 요청이면 200 을 반환하고 삭제를 처리한다")
		void removesItem() throws Exception {
			// when & then
			mockMvc.perform(delete(BASE_URL + "/items/10").header(HttpHeaders.AUTHORIZATION, bearer()))
					.andExpect(status().isOk());
			verify(cartService).removeItem(1L, 10L);
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(delete(BASE_URL + "/items/10"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(cartService, never()).removeItem(any(), any());
		}
	}

	@Nested
	@DisplayName("DELETE /api/v1/cart/items")
	class Clear {

		@Test
		@DisplayName("인증된 요청이면 200 을 반환하고 전체 삭제를 처리한다")
		void clearsCart() throws Exception {
			// when & then
			mockMvc.perform(delete(BASE_URL + "/items").header(HttpHeaders.AUTHORIZATION, bearer()))
					.andExpect(status().isOk());
			verify(cartService).clear(1L);
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(delete(BASE_URL + "/items"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(cartService, never()).clear(any());
		}
	}
}
