package com.groove.limited;

import static com.groove.limited.service.LimitedDropRedisService.STOCK_KEY_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
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
import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditLog;
import com.groove.admin.repository.AdminAuditLogRepository;
import com.groove.auth.jwt.JwtProvider;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.inventory.repository.StockRepository;
import com.groove.limited.dto.LimitedDropCreateRequest;
import com.groove.limited.dto.LimitedDropUpdateRequest;
import com.groove.limited.repository.LimitedDropRepository;
import com.groove.member.entity.Member;
import com.groove.member.entity.MemberRole;
import com.groove.member.repository.MemberRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;

@AutoConfigureMockMvc
class AdminLimitedDropFlowIntegrationTest extends IntegrationTestSupport {

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
	LimitedDropRepository limitedDropRepository;

	@Autowired
	AdminAuditLogRepository adminAuditLogRepository;

	@Autowired
	StringRedisTemplate redisTemplate;

	@Nested
	@DisplayName("등록 → 수정 → 강제 오픈 → 마감 흐름")
	class RegisterUpdateOpenAndCloseFlow {

		@Test
		@DisplayName("전체 흐름을 정상적으로 완료하고 감사 로그를 순서대로 남긴다")
		void completesFullAdminLimitedDropFlow() throws Exception {
			// given
			Member admin = memberRepository.save(
					Member.create("admin-" + UUID.randomUUID() + "@groove.com", "encoded", "관리자"));
			String adminToken = "Bearer " + jwtProvider.createAccessToken(admin.getId(), MemberRole.ADMIN);
			Long productId = createProductWithStock(10);
			LimitedDropCreateRequest createRequest = new LimitedDropCreateRequest(productId, 100, 2,
					LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2));

			// when
			MvcResult createResult = mockMvc.perform(post("/api/v1/admin/limited-drops")
							.header(HttpHeaders.AUTHORIZATION, adminToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(createRequest)))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.status", is("SCHEDULED")))
					.andExpect(jsonPath("$.data.remainingQuantity", is(100)))
					.andReturn();
			Long dropId = objectMapper.readTree(createResult.getResponse().getContentAsString())
					.path("data").path("id").asLong();
			assertThat(stockRepository.findByProductId(productId).orElseThrow().getQuantity()).isEqualTo(100);

			mockMvc.perform(get("/api/v1/admin/limited-drops")
							.header(HttpHeaders.AUTHORIZATION, adminToken)
							.param("status", "SCHEDULED"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.content[*].id", hasItem(dropId.intValue())));

			LimitedDropUpdateRequest updateRequest = new LimitedDropUpdateRequest(80, null, null, null);
			mockMvc.perform(patch("/api/v1/admin/limited-drops/{id}", dropId)
							.header(HttpHeaders.AUTHORIZATION, adminToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(updateRequest)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.totalQuantity", is(80)));
			assertThat(stockRepository.findByProductId(productId).orElseThrow().getQuantity()).isEqualTo(80);

			mockMvc.perform(patch("/api/v1/admin/limited-drops/{id}/open", dropId)
							.header(HttpHeaders.AUTHORIZATION, adminToken))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status", is("OPEN")));
			assertThat(redisTemplate.opsForValue().get(STOCK_KEY_PREFIX + dropId)).isEqualTo("80");

			// 이미 OPEN 인 드롭을 다시 오픈해도 SET NX 라 값이 바뀌지 않는다.
			mockMvc.perform(patch("/api/v1/admin/limited-drops/{id}/open", dropId)
							.header(HttpHeaders.AUTHORIZATION, adminToken))
					.andExpect(status().isOk());
			assertThat(redisTemplate.opsForValue().get(STOCK_KEY_PREFIX + dropId)).isEqualTo("80");

			mockMvc.perform(patch("/api/v1/admin/limited-drops/{id}", dropId)
							.header(HttpHeaders.AUTHORIZATION, adminToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(updateRequest)))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error.code", is("LIMITED_INVALID_STATUS")));

			mockMvc.perform(patch("/api/v1/admin/limited-drops/{id}/close", dropId)
							.header(HttpHeaders.AUTHORIZATION, adminToken))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status", is("CLOSED")));
			assertThat(redisTemplate.hasKey(STOCK_KEY_PREFIX + dropId)).isFalse();
			assertThat(stockRepository.findByProductId(productId).orElseThrow().getQuantity()).isEqualTo(80);

			// 이미 CLOSED 인 드롭을 다시 마감해도 그대로 200 이다.
			mockMvc.perform(patch("/api/v1/admin/limited-drops/{id}/close", dropId)
							.header(HttpHeaders.AUTHORIZATION, adminToken))
					.andExpect(status().isOk());

			// then
			List<AdminAuditLog> logs = adminAuditLogRepository.findAllByAdminIdOrderByIdAsc(admin.getId());
			assertThat(logs).extracting(AdminAuditLog::getAction)
					.containsExactly(AdminAuditAction.LIMITED_DROP_CREATE, AdminAuditAction.LIMITED_DROP_UPDATE,
							AdminAuditAction.LIMITED_DROP_OPEN, AdminAuditAction.LIMITED_DROP_CLOSE);
		}

		@Test
		@DisplayName("이미 드롭이 등록된 상품으로 다시 등록하면 409 LIMITED_DROP_ALREADY_EXISTS 를 반환한다")
		void rejectsDuplicateDropForSameProduct() throws Exception {
			// given
			Member admin = memberRepository.save(
					Member.create("admin-" + UUID.randomUUID() + "@groove.com", "encoded", "관리자"));
			String adminToken = "Bearer " + jwtProvider.createAccessToken(admin.getId(), MemberRole.ADMIN);
			Long productId = createProductWithStock(10);
			LimitedDropCreateRequest request = new LimitedDropCreateRequest(productId, 100, 2,
					LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2));

			mockMvc.perform(post("/api/v1/admin/limited-drops")
							.header(HttpHeaders.AUTHORIZATION, adminToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isCreated());

			// when & then
			mockMvc.perform(post("/api/v1/admin/limited-drops")
							.header(HttpHeaders.AUTHORIZATION, adminToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error.code", is("LIMITED_DROP_ALREADY_EXISTS")));
		}

		@Test
		@DisplayName("일반 회원 토큰으로 등록을 시도하면 403 을 반환한다")
		void rejectsCreateWithUserToken() throws Exception {
			// given
			Member user = memberRepository.save(
					Member.create("user-" + UUID.randomUUID() + "@groove.com", "encoded", "회원"));
			String userToken = "Bearer " + jwtProvider.createAccessToken(user.getId(), MemberRole.USER);
			Long productId = createProductWithStock(10);
			LimitedDropCreateRequest request = new LimitedDropCreateRequest(productId, 100, 2,
					LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2));

			// when & then
			mockMvc.perform(post("/api/v1/admin/limited-drops")
							.header(HttpHeaders.AUTHORIZATION, userToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
		}

		@Test
		@DisplayName("존재하지 않는 드롭을 오픈하려 하면 404 LIMITED_DROP_NOT_FOUND 를 반환한다")
		void returnsNotFoundForUnknownDropOnOpen() throws Exception {
			// given
			Member admin = memberRepository.save(
					Member.create("admin-" + UUID.randomUUID() + "@groove.com", "encoded", "관리자"));
			String adminToken = "Bearer " + jwtProvider.createAccessToken(admin.getId(), MemberRole.ADMIN);

			// when & then
			mockMvc.perform(patch("/api/v1/admin/limited-drops/{id}/open", 999999L)
							.header(HttpHeaders.AUTHORIZATION, adminToken))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code", is("LIMITED_DROP_NOT_FOUND")));
		}
	}

	private Long createProductWithStock(int quantity) {
		Artist artist = artistRepository.save(ArtistFixture.create());
		Product product = productRepository.save(ProductFixture.create(artist));
		stockRepository.save(StockFixture.create(product, quantity));
		return product.getId();
	}
}
