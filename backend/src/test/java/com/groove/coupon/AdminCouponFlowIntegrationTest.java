package com.groove.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditLog;
import com.groove.admin.repository.AdminAuditLogRepository;
import com.groove.auth.jwt.JwtProvider;
import com.groove.coupon.dto.CouponCreateRequest;
import com.groove.coupon.dto.CouponUpdateRequest;
import com.groove.coupon.entity.CouponStatus;
import com.groove.coupon.entity.DiscountType;
import com.groove.member.entity.Member;
import com.groove.member.entity.MemberRole;
import com.groove.member.repository.MemberRepository;
import com.groove.support.IntegrationTestSupport;

@AutoConfigureMockMvc
class AdminCouponFlowIntegrationTest extends IntegrationTestSupport {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	JwtProvider jwtProvider;

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	AdminAuditLogRepository adminAuditLogRepository;

	@Nested
	@DisplayName("등록 → 목록 → 수정 → 비활성화 흐름")
	class CreateListUpdateAndDisableFlow {

		@Test
		@DisplayName("전체 흐름을 정상적으로 완료하고 감사 로그를 순서대로 남긴다")
		void completesFullAdminCouponFlow() throws Exception {
			// given
			Member admin = memberRepository.save(
					Member.create("admin-" + UUID.randomUUID() + "@groove.com", "encoded", "관리자"));
			String adminToken = "Bearer " + jwtProvider.createAccessToken(admin.getId(), MemberRole.ADMIN);
			String code = "FLOW" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();

			CouponCreateRequest createRequest = new CouponCreateRequest(code, "플로우 쿠폰", DiscountType.FIXED,
					BigDecimal.valueOf(1000), null, null, 10, LocalDateTime.now().plusDays(7));

			// when
			MvcResult createResult = mockMvc.perform(post("/api/v1/admin/coupons")
							.header(HttpHeaders.AUTHORIZATION, adminToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(createRequest)))
					.andExpect(status().isCreated())
					.andReturn();
			Long couponId = objectMapper.readTree(createResult.getResponse().getContentAsString())
					.path("data").path("id").asLong();

			MvcResult listResult = mockMvc.perform(get("/api/v1/admin/coupons")
							.header(HttpHeaders.AUTHORIZATION, adminToken))
					.andExpect(status().isOk())
					.andReturn();
			var content = objectMapper.readTree(listResult.getResponse().getContentAsString())
					.path("data").path("content");
			boolean foundAfterCreate = false;
			for (var node : content) {
				if (node.path("id").asLong() == couponId) {
					foundAfterCreate = true;
					assertThat(node.path("issuedCount").asInt()).isZero();
					assertThat(node.path("usedCount").asLong()).isZero();
				}
			}
			assertThat(foundAfterCreate).isTrue();

			CouponUpdateRequest updateRequest = new CouponUpdateRequest("변경된 이름", null, null, null, null, 20, null,
					null);
			mockMvc.perform(patch("/api/v1/admin/coupons/{id}", couponId)
							.header(HttpHeaders.AUTHORIZATION, adminToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(updateRequest)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.name", is("변경된 이름")))
					.andExpect(jsonPath("$.data.totalQuantity", is(20)));

			// 만료일을 미리 늘려두고 비활성화한다. 이후 재활성화 요청이 이 만료일로 통과하는지 확인한다.
			LocalDateTime extendedExpiresAt = LocalDateTime.now().plusDays(30);
			CouponUpdateRequest reactivateRequest = new CouponUpdateRequest(null, null, null, null, null, null,
					extendedExpiresAt, CouponStatus.DISABLED);
			mockMvc.perform(patch("/api/v1/admin/coupons/{id}", couponId)
							.header(HttpHeaders.AUTHORIZATION, adminToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(reactivateRequest)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status", is("DISABLED")));

			CouponUpdateRequest activateRequest = new CouponUpdateRequest(null, null, null, null, null, null,
					extendedExpiresAt, CouponStatus.ACTIVE);
			mockMvc.perform(patch("/api/v1/admin/coupons/{id}", couponId)
							.header(HttpHeaders.AUTHORIZATION, adminToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(activateRequest)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status", is("ACTIVE")));

			mockMvc.perform(delete("/api/v1/admin/coupons/{id}", couponId)
							.header(HttpHeaders.AUTHORIZATION, adminToken))
					.andExpect(status().isOk());

			// then
			MvcResult disabledListResult = mockMvc.perform(get("/api/v1/admin/coupons")
							.header(HttpHeaders.AUTHORIZATION, adminToken)
							.param("status", "DISABLED"))
					.andExpect(status().isOk())
					.andReturn();
			var disabledContent = objectMapper.readTree(disabledListResult.getResponse().getContentAsString())
					.path("data").path("content");
			boolean foundAfterDisable = false;
			for (var node : disabledContent) {
				if (node.path("id").asLong() == couponId) {
					foundAfterDisable = true;
				}
			}
			assertThat(foundAfterDisable).isTrue();

			List<AdminAuditLog> logs = adminAuditLogRepository.findAllByAdminIdOrderByIdAsc(admin.getId());
			assertThat(logs).extracting(AdminAuditLog::getAction)
					.containsExactly(AdminAuditAction.COUPON_CREATE, AdminAuditAction.COUPON_UPDATE,
							AdminAuditAction.COUPON_UPDATE, AdminAuditAction.COUPON_UPDATE,
							AdminAuditAction.COUPON_DISABLE);
		}

		@Test
		@DisplayName("일반 회원 토큰으로 등록을 시도하면 403 을 반환한다")
		void rejectsCreateWithUserToken() throws Exception {
			// given
			Member user = memberRepository.save(
					Member.create("user-" + UUID.randomUUID() + "@groove.com", "encoded", "회원"));
			String userToken = "Bearer " + jwtProvider.createAccessToken(user.getId(), MemberRole.USER);
			String code = "USERTRY" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
			CouponCreateRequest request = new CouponCreateRequest(code, "일반회원 시도 쿠폰", DiscountType.FIXED,
					BigDecimal.valueOf(1000), null, null, null, LocalDateTime.now().plusDays(7));

			// when & then
			mockMvc.perform(post("/api/v1/admin/coupons")
							.header(HttpHeaders.AUTHORIZATION, userToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
		}

		@Test
		@DisplayName("중복된 코드로 등록하면 409 COUPON_CODE_DUPLICATE 를 반환한다")
		void rejectsCreateWithDuplicateCode() throws Exception {
			// given
			Member admin = memberRepository.save(
					Member.create("admin-" + UUID.randomUUID() + "@groove.com", "encoded", "관리자"));
			String adminToken = "Bearer " + jwtProvider.createAccessToken(admin.getId(), MemberRole.ADMIN);
			String code = "DUP" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
			CouponCreateRequest request = new CouponCreateRequest(code, "중복 쿠폰", DiscountType.FIXED,
					BigDecimal.valueOf(1000), null, null, null, LocalDateTime.now().plusDays(7));

			mockMvc.perform(post("/api/v1/admin/coupons")
							.header(HttpHeaders.AUTHORIZATION, adminToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isCreated());

			// when & then
			mockMvc.perform(post("/api/v1/admin/coupons")
							.header(HttpHeaders.AUTHORIZATION, adminToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error.code", is("COUPON_CODE_DUPLICATE")));
		}
	}
}
