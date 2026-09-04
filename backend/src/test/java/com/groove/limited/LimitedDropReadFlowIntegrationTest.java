package com.groove.limited;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.auth.jwt.JwtProvider;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.inventory.repository.StockRepository;
import com.groove.limited.dto.LimitedDropCreateRequest;
import com.groove.limited.service.LimitedDropRedisService;
import com.groove.member.entity.Member;
import com.groove.member.entity.MemberRole;
import com.groove.member.repository.MemberRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;

@AutoConfigureMockMvc
class LimitedDropReadFlowIntegrationTest extends IntegrationTestSupport {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	JwtProvider jwtProvider;

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	ArtistRepository artistRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	StockRepository stockRepository;

	@Autowired
	StringRedisTemplate redisTemplate;

	@Nested
	@DisplayName("공개 목록/상세 조회")
	class PublicReadFlow {

		@Test
		@DisplayName("OPEN 상태에서는 Redis 값을, 마감 후에는 DB 값을 remainingQuantity 로 내려준다")
		void readsRedisWhenOpenAndDbWhenClosed() throws Exception {
			// given
			String adminToken = adminToken();
			Long productId = createProductWithStock(100);
			LimitedDropCreateRequest createRequest = new LimitedDropCreateRequest(productId, 100, 2,
					LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2));

			MvcResult createResult = mockMvc.perform(post("/api/v1/admin/limited-drops")
							.header(HttpHeaders.AUTHORIZATION, adminToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(createRequest)))
					.andExpect(status().isCreated())
					.andReturn();
			Long dropId = objectMapper.readTree(createResult.getResponse().getContentAsString())
					.path("data").path("id").asLong();

			mockMvc.perform(patch("/api/v1/admin/limited-drops/{id}/open", dropId)
							.header(HttpHeaders.AUTHORIZATION, adminToken))
					.andExpect(status().isOk());

			// Redis 재고를 DB(100) 와 다른 값으로 덮어써 공개 상세가 Redis 를 읽는다는 걸 확인한다.
			redisTemplate.opsForValue().set(LimitedDropRedisService.stockKey(dropId), "37");

			// when & then
			mockMvc.perform(get("/api/v1/limited-drops/{id}", dropId))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.remainingQuantity", is(37)))
					.andExpect(jsonPath("$.data.status", is("OPEN")));

			mockMvc.perform(get("/api/v1/limited-drops").param("status", "OPEN"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.drops[*].id", hasItem(dropId.intValue())));

			// close 되면 Redis 키가 지워지고 DB 값(100 - soldCount)으로 응답한다.
			mockMvc.perform(patch("/api/v1/admin/limited-drops/{id}/close", dropId)
							.header(HttpHeaders.AUTHORIZATION, adminToken))
					.andExpect(status().isOk());

			mockMvc.perform(get("/api/v1/limited-drops/{id}", dropId))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.remainingQuantity", is(100)))
					.andExpect(jsonPath("$.data.status", is("CLOSED")));

			mockMvc.perform(get("/api/v1/limited-drops"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.drops[*].id", not(hasItem(dropId.intValue()))));
		}
	}

	private String adminToken() {
		Member admin = memberRepository.save(
				Member.create("admin-" + UUID.randomUUID() + "@groove.com", "encoded", "관리자"));
		return "Bearer " + jwtProvider.createAccessToken(admin.getId(), MemberRole.ADMIN);
	}

	private Long createProductWithStock(int quantity) {
		Artist artist = artistRepository.save(ArtistFixture.create());
		Product product = productRepository.save(ProductFixture.create(artist));
		stockRepository.save(StockFixture.create(product, quantity));
		return product.getId();
	}
}
