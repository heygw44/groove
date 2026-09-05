package com.groove.cart;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.auth.dto.LoginRequest;
import com.groove.auth.dto.SignupRequest;
import com.groove.cart.dto.CartItemAddRequest;
import com.groove.cart.dto.CartItemQuantityUpdateRequest;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.inventory.repository.StockRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;

@AutoConfigureMockMvc
class CartFlowIntegrationTest extends IntegrationTestSupport {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	ArtistRepository artistRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	StockRepository stockRepository;

	@Nested
	@DisplayName("담기 → 조회 → 타인 수정 차단 → 비우기 흐름")
	class CartFlow {

		@Test
		@DisplayName("두 번 담으면 수량이 합산되고, 비우면 빈 카트가 된다")
		void addsUpQuantityAndClearsCart() throws Exception {
			// given
			String accessToken = signupAndLogin().accessToken();
			Product product = seedProduct(10);

			// when: 같은 상품을 두 번 담는다
			addItem(accessToken, new CartItemAddRequest(product.getId(), 2));
			addItem(accessToken, new CartItemAddRequest(product.getId(), 3));

			// then: 수량이 합산되고 총액이 계산된다
			mockMvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.items[0].quantity", is(5)))
					.andExpect(jsonPath("$.data.totalAmount", is(225000.0)));

			// when: 장바구니를 비운다
			mockMvc.perform(delete("/api/v1/cart/items").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
					.andExpect(status().isOk());

			// then: 빈 카트가 된다
			mockMvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.items", hasSize(0)));
		}

		@Test
		@DisplayName("다른 회원이 항목을 수정하려 하면 404 CART_ITEM_NOT_FOUND 를 반환한다")
		void returnsNotFoundWhenOtherMemberUpdatesItem() throws Exception {
			// given
			String ownerToken = signupAndLogin().accessToken();
			Product product = seedProduct(10);
			MvcResult addResult = addItem(ownerToken, new CartItemAddRequest(product.getId(), 1));
			Long cartItemId = objectMapper.readTree(addResult.getResponse().getContentAsString())
					.path("data").path("id").asLong();
			String otherToken = signupAndLogin().accessToken();

			// when & then
			mockMvc.perform(patch("/api/v1/cart/items/" + cartItemId)
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new CartItemQuantityUpdateRequest(2))))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code", is("CART_ITEM_NOT_FOUND")));
		}
	}

	private Product seedProduct(int stockQuantity) {
		Artist artist = artistRepository.save(ArtistFixture.create());
		Product product = productRepository.save(ProductFixture.create(artist));
		stockRepository.save(StockFixture.create(product, stockQuantity));
		return product;
	}

	private MvcResult addItem(String accessToken, CartItemAddRequest request) throws Exception {
		return mockMvc.perform(post("/api/v1/cart/items")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andReturn();
	}

	private SignedUpMember signupAndLogin() throws Exception {
		String email = "cart-flow-" + UUID.randomUUID() + "@groove.com";
		String password = "password1";
		mockMvc.perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new SignupRequest(email, password, "그루버"))))
				.andExpect(status().isCreated());

		MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
				.andExpect(status().isOk())
				.andReturn();
		String accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
				.path("data").path("accessToken").asText();
		return new SignedUpMember(accessToken);
	}

	private record SignedUpMember(String accessToken) {
	}
}
