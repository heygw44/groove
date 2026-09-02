package com.groove.auth.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.auth.dto.SignupRequest;
import com.groove.auth.dto.SignupResponse;
import com.groove.auth.jwt.JwtProvider;
import com.groove.auth.service.AuthService;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
	JwtProvider.class})
@ActiveProfiles("test")
class AuthControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@MockitoBean
	AuthService authService;

	@Nested
	@DisplayName("POST /api/v1/auth/signup")
	class Signup {

		@Test
		@DisplayName("유효한 요청이면 201 과 회원가입 응답을 반환한다")
		void returnsCreatedWithSignupResponse() throws Exception {
			// given
			SignupRequest request = new SignupRequest("groover@groove.com", "password1", "그루버");
			SignupResponse response = new SignupResponse(1L, request.email(), request.nickname());
			given(authService.signup(any(SignupRequest.class))).willReturn(response);

			// when & then
			mockMvc.perform(post("/api/v1/auth/signup")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.success", is(true)))
					.andExpect(jsonPath("$.data.id", is(1)))
					.andExpect(jsonPath("$.data.email", is(request.email())))
					.andExpect(jsonPath("$.data.nickname", is(request.nickname())));
		}

		@ParameterizedTest
		@DisplayName("필드 값이 유효하지 않으면 400 과 필드 에러를 반환한다")
		@CsvSource({
			"invalid-email, password1, 그루버, email",
			"groover@groove.com, short12, 그루버, password",
			"groover@groove.com, password12345678901234, 그루버, password",
			"groover@groove.com, password1, 그, nickname",
			"'', password1, 그루버, email"
		})
		void returnsBadRequestWhenFieldInvalid(String email, String password, String nickname,
				String expectedField) throws Exception {
			// given
			SignupRequest request = new SignupRequest(email, password, nickname);

			// when & then
			mockMvc.perform(post("/api/v1/auth/signup")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")))
					.andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem(expectedField)));
		}

		@Test
		@DisplayName("이미 가입된 이메일이면 409 와 MEMBER_EMAIL_DUPLICATE 를 반환한다")
		void returnsConflictWhenEmailDuplicated() throws Exception {
			// given
			SignupRequest request = new SignupRequest("groover@groove.com", "password1", "그루버");
			willThrow(new BusinessException(ErrorCode.MEMBER_EMAIL_DUPLICATE))
					.given(authService).signup(any(SignupRequest.class));

			// when & then
			mockMvc.perform(post("/api/v1/auth/signup")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error.code", is("MEMBER_EMAIL_DUPLICATE")));
		}
	}
}
