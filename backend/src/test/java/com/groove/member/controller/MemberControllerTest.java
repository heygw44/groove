package com.groove.member.controller;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

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
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;
import com.groove.member.dto.MemberResponse;
import com.groove.member.dto.MemberUpdateRequest;
import com.groove.member.dto.PasswordChangeRequest;
import com.groove.member.entity.MemberRole;
import com.groove.member.entity.MemberStatus;
import com.groove.member.service.MemberService;

@WebMvcTest(MemberController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
	JwtProvider.class})
@ActiveProfiles("test")
class MemberControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	JwtProvider jwtProvider;

	@MockitoBean
	MemberService memberService;

	private String bearer() {
		return "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.USER);
	}

	@Nested
	@DisplayName("GET /api/v1/members/me")
	class GetMyInfo {

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환하고 서비스는 호출되지 않는다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(get("/api/v1/members/me"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(memberService, never()).getMyInfo(any());
		}

		@Test
		@DisplayName("인증된 요청이면 200 과 회원 정보를 반환한다")
		void returnsMemberInfo() throws Exception {
			// given
			MemberResponse response = new MemberResponse(1L, "groover@groove.com", "그루버", MemberRole.USER,
					MemberStatus.ACTIVE, LocalDateTime.of(2026, 1, 15, 9, 20));
			given(memberService.getMyInfo(1L)).willReturn(response);

			// when & then
			mockMvc.perform(get("/api/v1/members/me").header(HttpHeaders.AUTHORIZATION, bearer()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.id", is(1)))
					.andExpect(jsonPath("$.data.email", is("groover@groove.com")))
					.andExpect(jsonPath("$.data.nickname", is("그루버")))
					.andExpect(jsonPath("$.data.role", is("USER")))
					.andExpect(jsonPath("$.data.status", is("ACTIVE")))
					.andExpect(jsonPath("$.data.createdAt", is("2026-01-15T09:20:00")));
		}

		@Test
		@DisplayName("탈퇴한 회원이면 403 MEMBER_WITHDRAWN 을 반환한다")
		void returnsForbiddenWhenMemberWithdrawn() throws Exception {
			// given
			willThrow(new BusinessException(ErrorCode.MEMBER_WITHDRAWN)).given(memberService).getMyInfo(1L);

			// when & then
			mockMvc.perform(get("/api/v1/members/me").header(HttpHeaders.AUTHORIZATION, bearer()))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("MEMBER_WITHDRAWN")));
		}
	}

	@Nested
	@DisplayName("PATCH /api/v1/members/me")
	class UpdateNickname {

		@Test
		@DisplayName("유효한 닉네임이면 200 과 변경된 회원 정보를 반환한다")
		void updatesNickname() throws Exception {
			// given
			MemberUpdateRequest request = new MemberUpdateRequest("새그루버");
			MemberResponse response = new MemberResponse(1L, "groover@groove.com", "새그루버", MemberRole.USER,
					MemberStatus.ACTIVE, LocalDateTime.of(2026, 1, 15, 9, 20));
			given(memberService.updateNickname(eq(1L), any())).willReturn(response);

			// when & then
			mockMvc.perform(patch("/api/v1/members/me")
							.header(HttpHeaders.AUTHORIZATION, bearer())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.nickname", is("새그루버")));
			verify(memberService).updateNickname(eq(1L), any());
		}

		@ParameterizedTest
		@DisplayName("닉네임 값이 유효하지 않으면 400 과 필드 에러를 반환한다")
		@ValueSource(strings = {"", "그", "가나다라마바사아자차카타파하가나다라마바사"})
		void returnsBadRequestWhenNicknameInvalid(String nickname) throws Exception {
			// given
			MemberUpdateRequest request = new MemberUpdateRequest(nickname);

			// when & then
			mockMvc.perform(patch("/api/v1/members/me")
							.header(HttpHeaders.AUTHORIZATION, bearer())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")))
					.andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem("nickname")));
		}
	}

	@Nested
	@DisplayName("PATCH /api/v1/members/me/password")
	class ChangePassword {

		@Test
		@DisplayName("유효한 요청이면 200 을 반환한다")
		void changesPassword() throws Exception {
			// given
			PasswordChangeRequest request = new PasswordChangeRequest("current-password1", "new-password1");

			// when & then
			mockMvc.perform(patch("/api/v1/members/me/password")
							.header(HttpHeaders.AUTHORIZATION, bearer())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isOk());
			verify(memberService).changePassword(eq(1L), any());
		}

		@Test
		@DisplayName("새 비밀번호가 너무 짧으면 400 과 필드 에러를 반환한다")
		void returnsBadRequestWhenNewPasswordTooShort() throws Exception {
			// given
			PasswordChangeRequest request = new PasswordChangeRequest("current-password1", "short1");

			// when & then
			mockMvc.perform(patch("/api/v1/members/me/password")
							.header(HttpHeaders.AUTHORIZATION, bearer())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")))
					.andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem("newPassword")));
		}

		@Test
		@DisplayName("현재 비밀번호가 일치하지 않으면 400 MEMBER_PASSWORD_MISMATCH 를 반환한다")
		void returnsBadRequestWhenCurrentPasswordMismatch() throws Exception {
			// given
			PasswordChangeRequest request = new PasswordChangeRequest("wrong-password", "new-password1");
			willThrow(new BusinessException(ErrorCode.MEMBER_PASSWORD_MISMATCH))
					.given(memberService).changePassword(eq(1L), any());

			// when & then
			mockMvc.perform(patch("/api/v1/members/me/password")
							.header(HttpHeaders.AUTHORIZATION, bearer())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("MEMBER_PASSWORD_MISMATCH")));
		}
	}

	@Nested
	@DisplayName("DELETE /api/v1/members/me")
	class Withdraw {

		@Test
		@DisplayName("인증된 요청이면 200 을 반환하고 탈퇴를 처리한다")
		void withdrawsMember() throws Exception {
			// when & then
			mockMvc.perform(delete("/api/v1/members/me").header(HttpHeaders.AUTHORIZATION, bearer()))
					.andExpect(status().isOk());
			verify(memberService).withdraw(1L);
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(delete("/api/v1/members/me"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(memberService, never()).withdraw(any());
		}
	}
}
