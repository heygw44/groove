package com.groove.wishlist;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.auth.dto.LoginRequest;
import com.groove.auth.dto.SignupRequest;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.ProductFixture;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;
import com.groove.wishlist.dto.WishlistAddRequest;

@AutoConfigureMockMvc
class WishlistFlowIntegrationTest extends IntegrationTestSupport {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	ArtistRepository artistRepository;

	@Autowired
	ProductRepository productRepository;

	@Nested
	@DisplayName("등록 → 중복 등록 → 조회 → 삭제 → 재삭제 흐름")
	class WishlistFlow {

		@Test
		@DisplayName("등록 후 중복 등록은 409, 삭제 후 재삭제는 404 를 반환한다")
		void addAndRemoveFlow() throws Exception {
			// given
			String accessToken = signupAndLogin();
			Product product = seedProduct();

			// when: 위시리스트에 등록한다
			mockMvc.perform(post("/api/v1/wishlist")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new WishlistAddRequest(product.getId()))))
					.andExpect(status().isCreated());

			// then: 같은 상품을 다시 등록하면 409 를 반환한다
			mockMvc.perform(post("/api/v1/wishlist")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new WishlistAddRequest(product.getId()))))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error.code", is("WISHLIST_ALREADY_EXISTS")));

			// then: 목록에 항목이 하나 조회된다
			mockMvc.perform(get("/api/v1/wishlist").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.content", hasSize(1)))
					.andExpect(jsonPath("$.data.content[0].productId", is(product.getId().intValue())));

			// when: 삭제한다
			mockMvc.perform(delete("/api/v1/wishlist/" + product.getId())
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
					.andExpect(status().isOk());

			// then: 다시 삭제하면 404 를 반환한다
			mockMvc.perform(delete("/api/v1/wishlist/" + product.getId())
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code", is("WISHLIST_NOT_FOUND")));

			// then: 목록이 비어 있다
			mockMvc.perform(get("/api/v1/wishlist").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.content", hasSize(0)));
		}

		@Test
		@DisplayName("위시리스트에 등록한 상품은 상품 조회 응답에도 wishlisted 로 반영된다")
		void reflectsWishlistedInProductResponses() throws Exception {
			// given
			String accessToken = signupAndLogin();
			Product product = seedProduct();
			mockMvc.perform(post("/api/v1/wishlist")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new WishlistAddRequest(product.getId()))))
					.andExpect(status().isCreated());

			// then: 로그인 상태로 상세를 조회하면 wishlisted 가 true 다
			mockMvc.perform(get("/api/v1/products/" + product.getId())
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.wishlisted", is(true)));

			// then: 비로그인으로 상세를 조회하면 wishlisted 키가 없다
			mockMvc.perform(get("/api/v1/products/" + product.getId()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.wishlisted").doesNotExist());

			// then: 로그인 상태로 목록을 조회하면 wishlisted 가 true 다
			mockMvc.perform(get("/api/v1/products")
							.param("keyword", product.getTitle())
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.content[0].wishlisted", is(true)));
		}

		@Test
		@DisplayName("숨김 처리된 상품은 목록에서 제외된다")
		void excludesHiddenProduct() throws Exception {
			// given
			String accessToken = signupAndLogin();
			Product visibleProduct = seedProduct();
			Product hiddenProduct = seedProduct();
			hiddenProduct.hide();
			productRepository.save(hiddenProduct);

			mockMvc.perform(post("/api/v1/wishlist")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(
									new WishlistAddRequest(visibleProduct.getId()))))
					.andExpect(status().isCreated());

			// when & then
			mockMvc.perform(get("/api/v1/wishlist").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.content", hasSize(1)))
					.andExpect(jsonPath("$.data.content[0].productId", is(visibleProduct.getId().intValue())));
		}
	}

	private Product seedProduct() {
		Artist artist = artistRepository.save(ArtistFixture.create());
		return productRepository.save(ProductFixture.create(artist));
	}

	private String signupAndLogin() throws Exception {
		String email = "wishlist-flow-" + UUID.randomUUID() + "@groove.com";
		String password = "password1";
		mockMvc.perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new SignupRequest(email, password, "그루버"))))
				.andExpect(status().isCreated());

		String responseBody = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
		return objectMapper.readTree(responseBody).path("data").path("accessToken").asText();
	}
}
