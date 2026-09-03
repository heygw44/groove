package com.groove.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditLog;
import com.groove.admin.repository.AdminAuditLogRepository;
import com.groove.auth.jwt.JwtProvider;
import com.groove.member.entity.Member;
import com.groove.member.entity.MemberRole;
import com.groove.member.repository.MemberRepository;
import com.groove.product.dto.ProductCreateRequest;
import com.groove.product.dto.ProductUpdateRequest;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Genre;
import com.groove.product.entity.Label;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.GenreRepository;
import com.groove.product.repository.LabelRepository;
import com.groove.support.IntegrationTestSupport;

@AutoConfigureMockMvc
class AdminProductFlowIntegrationTest extends IntegrationTestSupport {

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
	LabelRepository labelRepository;

	@Autowired
	GenreRepository genreRepository;

	@Autowired
	AdminAuditLogRepository adminAuditLogRepository;

	@Nested
	@DisplayName("등록 → 수정 → 숨김 → 목록 조회 흐름")
	class CreateUpdateHideAndListFlow {

		@Test
		@DisplayName("전체 흐름을 정상적으로 완료하고 감사 로그를 순서대로 남긴다")
		void completesFullAdminProductFlow() throws Exception {
			// given
			Member admin = memberRepository.save(
					Member.create("admin-" + UUID.randomUUID() + "@groove.com", "encoded", "관리자"));
			String adminToken = "Bearer " + jwtProvider.createAccessToken(admin.getId(), MemberRole.ADMIN);
			Artist artist = artistRepository.save(Artist.create("Miles Davis", "Miles Davis", "설명"));
			Label label = labelRepository.save(Label.create("Blue Note", "US"));
			Genre genre = genreRepository.save(Genre.create("Jazz-" + UUID.randomUUID()));

			ProductCreateRequest createRequest = new ProductCreateRequest("Kind of Blue", artist.getId(),
					label.getId(), List.of(genre.getId()), LocalDate.of(1959, 8, 17), "180g", "Black",
					new BigDecimal("45000.00"), "설명",
					List.of("https://cdn.groove.com/0.jpg", "https://cdn.groove.com/1.jpg"), 10);

			// when
			MvcResult createResult = mockMvc.perform(post("/api/v1/admin/products")
							.header(HttpHeaders.AUTHORIZATION, adminToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(createRequest)))
					.andExpect(status().isCreated())
					.andReturn();
			Long productId = objectMapper.readTree(createResult.getResponse().getContentAsString())
					.path("data").path("id").asLong();

			ProductUpdateRequest updateRequest = new ProductUpdateRequest("A Love Supreme", null,
					JsonNullable.undefined(), null, null, null, null, new BigDecimal("58000.00"), null,
					List.of("https://cdn.groove.com/updated.jpg"));
			mockMvc.perform(patch("/api/v1/admin/products/{id}", productId)
							.header(HttpHeaders.AUTHORIZATION, adminToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(updateRequest)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.title", is("A Love Supreme")))
					.andExpect(jsonPath("$.data.price", is(58000.0)))
					.andExpect(jsonPath("$.data.images[0].url", is("https://cdn.groove.com/updated.jpg")));

			mockMvc.perform(delete("/api/v1/admin/products/{id}", productId)
							.header(HttpHeaders.AUTHORIZATION, adminToken))
					.andExpect(status().isOk());

			// then
			MvcResult listResult = mockMvc.perform(get("/api/v1/admin/products")
							.header(HttpHeaders.AUTHORIZATION, adminToken)
							.param("status", "HIDDEN"))
					.andExpect(status().isOk())
					.andReturn();
			var content = objectMapper.readTree(listResult.getResponse().getContentAsString())
					.path("data").path("content");
			boolean found = false;
			for (var node : content) {
				if (node.path("id").asLong() == productId) {
					found = true;
					assertThat(node.path("stockQuantity").asInt()).isEqualTo(10);
				}
			}
			assertThat(found).isTrue();

			List<AdminAuditLog> logs = adminAuditLogRepository.findAllByAdminIdOrderByIdAsc(admin.getId());
			assertThat(logs).extracting(AdminAuditLog::getAction)
					.containsExactly(AdminAuditAction.PRODUCT_CREATE, AdminAuditAction.PRODUCT_UPDATE,
							AdminAuditAction.PRODUCT_HIDE);
		}

		@Test
		@DisplayName("일반 회원 토큰으로 등록을 시도하면 403 을 반환한다")
		void rejectsCreateWithUserToken() throws Exception {
			// given
			Member user = memberRepository.save(
					Member.create("user-" + UUID.randomUUID() + "@groove.com", "encoded", "회원"));
			String userToken = "Bearer " + jwtProvider.createAccessToken(user.getId(), MemberRole.USER);
			Artist artist = artistRepository.save(Artist.create("Bill Evans", "Bill Evans", "설명"));
			ProductCreateRequest request = new ProductCreateRequest("Waltz for Debby", artist.getId(), null,
					List.of(), LocalDate.of(1961, 6, 25), "180g", "Black", new BigDecimal("40000.00"), "설명",
					List.of(), 5);

			// when & then
			mockMvc.perform(post("/api/v1/admin/products")
							.header(HttpHeaders.AUTHORIZATION, userToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
		}
	}

	@Nested
	@DisplayName("PATCH /admin/products/{id} labelId 삼중 상태")
	class UpdateLabelTriState {

		@Test
		@DisplayName("labelId 를 명시적으로 null 로 보내면 레이블을 해제한다")
		void clearsLabelWhenLabelIdIsExplicitNull() throws Exception {
			// given
			Member admin = memberRepository.save(
					Member.create("admin-" + UUID.randomUUID() + "@groove.com", "encoded", "관리자"));
			String adminToken = "Bearer " + jwtProvider.createAccessToken(admin.getId(), MemberRole.ADMIN);
			Artist artist = artistRepository.save(Artist.create("John Coltrane", "John Coltrane", "설명"));
			Label label = labelRepository.save(Label.create("Impulse!", "US"));

			ProductCreateRequest createRequest = new ProductCreateRequest("Blue Train", artist.getId(),
					label.getId(), List.of(), LocalDate.of(1957, 9, 15), "180g", "Black",
					new BigDecimal("40000.00"), "설명", List.of(), 5);
			MvcResult createResult = mockMvc.perform(post("/api/v1/admin/products")
							.header(HttpHeaders.AUTHORIZATION, adminToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(createRequest)))
					.andExpect(status().isCreated())
					.andReturn();
			Long productId = objectMapper.readTree(createResult.getResponse().getContentAsString())
					.path("data").path("id").asLong();

			// when
			mockMvc.perform(patch("/api/v1/admin/products/{id}", productId)
							.header(HttpHeaders.AUTHORIZATION, adminToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"labelId\":null}"))
					.andExpect(status().isOk());

			// then
			mockMvc.perform(get("/api/v1/products/{id}", productId))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.label").doesNotExist());
		}
	}

	@Nested
	@DisplayName("숨김 → 복구 흐름")
	class HideAndRestore {

		@Test
		@DisplayName("숨긴 상품을 복구하면 다시 노출된다")
		void hidesAndRestoresProduct() throws Exception {
			// given
			Member admin = memberRepository.save(
					Member.create("admin-" + UUID.randomUUID() + "@groove.com", "encoded", "관리자"));
			String adminToken = "Bearer " + jwtProvider.createAccessToken(admin.getId(), MemberRole.ADMIN);
			Artist artist = artistRepository.save(Artist.create("Herbie Hancock", "Herbie Hancock", "설명"));

			ProductCreateRequest createRequest = new ProductCreateRequest("Maiden Voyage", artist.getId(), null,
					List.of(), LocalDate.of(1965, 3, 17), "180g", "Black", new BigDecimal("42000.00"), "설명",
					List.of(), 5);
			MvcResult createResult = mockMvc.perform(post("/api/v1/admin/products")
							.header(HttpHeaders.AUTHORIZATION, adminToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(createRequest)))
					.andExpect(status().isCreated())
					.andReturn();
			Long productId = objectMapper.readTree(createResult.getResponse().getContentAsString())
					.path("data").path("id").asLong();

			// when
			mockMvc.perform(delete("/api/v1/admin/products/{id}", productId)
							.header(HttpHeaders.AUTHORIZATION, adminToken))
					.andExpect(status().isOk());

			mockMvc.perform(get("/api/v1/products/{id}", productId))
					.andExpect(status().isNotFound());

			mockMvc.perform(get("/api/v1/admin/products/{id}", productId)
							.header(HttpHeaders.AUTHORIZATION, adminToken))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status", is("HIDDEN")));

			mockMvc.perform(patch("/api/v1/admin/products/{id}/restore", productId)
							.header(HttpHeaders.AUTHORIZATION, adminToken))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status", is("ON_SALE")));

			// then
			mockMvc.perform(get("/api/v1/products/{id}", productId))
					.andExpect(status().isOk());
		}
	}
}
