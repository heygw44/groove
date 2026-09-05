package com.groove.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.groove.auth.dto.LoginRequest;
import com.groove.auth.dto.SignupRequest;
import com.groove.auth.jwt.JwtProvider;
import com.groove.fixture.MemberFixture;
import com.groove.member.dto.AdminMemberStatusChangeRequest;
import com.groove.member.entity.Member;
import com.groove.member.entity.MemberRole;
import com.groove.member.entity.MemberStatus;
import com.groove.member.repository.MemberRepository;
import com.groove.support.IntegrationTestSupport;

import jakarta.servlet.http.Cookie;

@AutoConfigureMockMvc
class AdminMemberFlowIntegrationTest extends IntegrationTestSupport {

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
	@DisplayName("회원 정지 → 로그인·재발급·내 정보 조회 거부 → 활성화 흐름")
	class SuspendAndActivateFlow {

		@Test
		@DisplayName("정지하면 로그인·재발급·보호 API 접근을 거부하고, 활성화하면 다시 로그인할 수 있다")
		void suspendsRejectsAccessThenActivateRestoresLogin() throws Exception {
			// given
			String email = "admin-member-flow-" + UUID.randomUUID() + "@groove.com";
			String password = "password1";
			MvcResult signupResult = signup(email, password);
			Long memberId = objectMapper.readTree(signupResult.getResponse().getContentAsString())
					.path("data").path("id").asLong();
			MvcResult loginResult = login(email, password);
			String accessToken = "Bearer " + objectMapper.readTree(loginResult.getResponse().getContentAsString())
					.path("data").path("accessToken").asText();
			Cookie refreshCookie = loginResult.getResponse().getCookie("refreshToken");

			Member admin = memberRepository.save(MemberFixture.createAdmin("admin-flow-" + UUID.randomUUID()
					+ "@groove.com"));
			String adminToken = "Bearer " + jwtProvider.createAccessToken(admin.getId(), MemberRole.ADMIN);

			// when: 정지
			mockMvc.perform(patch("/api/v1/admin/members/" + memberId + "/status")
							.header(HttpHeaders.AUTHORIZATION, adminToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(
									new AdminMemberStatusChangeRequest(MemberStatus.SUSPENDED, null))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status", is("SUSPENDED")));

			// then: 로그인·재발급·기존 access token 으로 보호 API 접근 모두 거부
			mockMvc.perform(post("/api/v1/auth/login")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_MEMBER_SUSPENDED")));

			mockMvc.perform(post("/api/v1/auth/reissue").cookie(refreshCookie))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_REFRESH_TOKEN_NOT_FOUND")));

			mockMvc.perform(get("/api/v1/members/me").header(HttpHeaders.AUTHORIZATION, accessToken))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_MEMBER_SUSPENDED")));

			List<AdminAuditLog> logs = adminAuditLogRepository.findAllByAdminIdOrderByIdAsc(admin.getId());
			assertThat(logs).extracting(AdminAuditLog::getAction).contains(AdminAuditAction.MEMBER_STATUS_CHANGE);
			assertThat(logs).extracting(AdminAuditLog::getDetail).contains("ACTIVE->SUSPENDED");

			// when: 활성화
			mockMvc.perform(patch("/api/v1/admin/members/" + memberId + "/status")
							.header(HttpHeaders.AUTHORIZATION, adminToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(
									new AdminMemberStatusChangeRequest(MemberStatus.ACTIVE, null))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status", is("ACTIVE")));

			// then: 다시 로그인할 수 있다
			mockMvc.perform(post("/api/v1/auth/login")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
					.andExpect(status().isOk());
		}
	}

	@Nested
	@DisplayName("자기 자신·다른 관리자 상태 변경 금지")
	class ProtectedTargets {

		@Test
		@DisplayName("관리자가 자기 자신을 정지하려 하면 403 ADMIN_CANNOT_MODIFY_SELF 를 반환한다")
		void rejectsSuspendingSelf() throws Exception {
			// given
			Member admin = memberRepository.save(
					MemberFixture.createAdmin("admin-self-" + UUID.randomUUID() + "@groove.com"));
			String adminToken = "Bearer " + jwtProvider.createAccessToken(admin.getId(), MemberRole.ADMIN);

			// when & then
			mockMvc.perform(patch("/api/v1/admin/members/" + admin.getId() + "/status")
							.header(HttpHeaders.AUTHORIZATION, adminToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(
									new AdminMemberStatusChangeRequest(MemberStatus.SUSPENDED, null))))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("ADMIN_CANNOT_MODIFY_SELF")));
		}

		@Test
		@DisplayName("다른 관리자를 정지하려 하면 403 ADMIN_CANNOT_MODIFY_ADMIN 을 반환한다")
		void rejectsSuspendingOtherAdmin() throws Exception {
			// given
			Member admin = memberRepository.save(
					MemberFixture.createAdmin("admin-actor-" + UUID.randomUUID() + "@groove.com"));
			Member otherAdmin = memberRepository.save(
					MemberFixture.createAdmin("admin-target-" + UUID.randomUUID() + "@groove.com"));
			String adminToken = "Bearer " + jwtProvider.createAccessToken(admin.getId(), MemberRole.ADMIN);

			// when & then
			mockMvc.perform(patch("/api/v1/admin/members/" + otherAdmin.getId() + "/status")
							.header(HttpHeaders.AUTHORIZATION, adminToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(
									new AdminMemberStatusChangeRequest(MemberStatus.SUSPENDED, null))))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("ADMIN_CANNOT_MODIFY_ADMIN")));
		}
	}

	@Nested
	@DisplayName("목록·상세 조회")
	class ListAndDetail {

		@Test
		@DisplayName("keyword 로 검색하면 회원의 주문 건수와 결제 합계를 함께 반환한다")
		void searchesByKeywordWithAggregates() throws Exception {
			// given
			String email = "admin-member-search-" + UUID.randomUUID() + "@groove.com";
			signup(email, "password1");
			Member admin = memberRepository.save(
					MemberFixture.createAdmin("admin-search-" + UUID.randomUUID() + "@groove.com"));
			String adminToken = "Bearer " + jwtProvider.createAccessToken(admin.getId(), MemberRole.ADMIN);

			// when & then
			mockMvc.perform(get("/api/v1/admin/members")
							.header(HttpHeaders.AUTHORIZATION, adminToken)
							.param("keyword", email))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.content[0].email", is(email)))
					.andExpect(jsonPath("$.data.content[0].orderCount", is(0)));
		}

		@Test
		@DisplayName("상세 조회는 최근 주문 목록을 포함한다")
		void detailIncludesRecentOrders() throws Exception {
			// given
			String email = "admin-member-detail-" + UUID.randomUUID() + "@groove.com";
			signup(email, "password1");
			Member member = memberRepository.findByEmail(email).orElseThrow();
			Member admin = memberRepository.save(
					MemberFixture.createAdmin("admin-detail-" + UUID.randomUUID() + "@groove.com"));
			String adminToken = "Bearer " + jwtProvider.createAccessToken(admin.getId(), MemberRole.ADMIN);

			// when & then
			mockMvc.perform(get("/api/v1/admin/members/" + member.getId())
							.header(HttpHeaders.AUTHORIZATION, adminToken))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.email", is(email)))
					.andExpect(jsonPath("$.data.recentOrders").isArray());
		}
	}

	private MvcResult signup(String email, String password) throws Exception {
		SignupRequest request = new SignupRequest(email, password, "그루버");
		return mockMvc.perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andReturn();
	}

	private MvcResult login(String email, String password) throws Exception {
		LoginRequest request = new LoginRequest(email, password);
		return mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andReturn();
	}
}
