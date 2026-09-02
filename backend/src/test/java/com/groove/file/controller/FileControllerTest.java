package com.groove.file.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.groove.auth.jwt.JwtProvider;
import com.groove.file.dto.FileUploadResponse;
import com.groove.file.service.FileService;
import com.groove.fixture.FileFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;
import com.groove.member.entity.MemberRole;

@WebMvcTest(FileController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
	JwtProvider.class})
@ActiveProfiles("test")
class FileControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtProvider jwtProvider;

	@MockitoBean
	FileService fileService;

	private String adminToken() {
		return "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.ADMIN);
	}

	private String userToken() {
		return "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.USER);
	}

	@Nested
	@DisplayName("POST /api/v1/files/images")
	class UploadImage {

		@Test
		@DisplayName("관리자면 200 과 업로드 결과를 반환한다")
		void returnsOkWhenAdmin() throws Exception {
			// given
			MockMultipartFile file = FileFixture.image();
			FileUploadResponse response = new FileUploadResponse(
					"http://localhost:8080/uploads/2026/09/02/uuid.jpg", "cover.jpg", file.getSize());
			given(fileService.uploadImage(any())).willReturn(response);

			// when & then
			mockMvc.perform(multipart("/api/v1/files/images")
							.file(file)
							.header(HttpHeaders.AUTHORIZATION, adminToken()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.url", is(response.url())));
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN 을 반환하고 서비스는 호출되지 않는다")
		void returnsForbiddenWhenNotAdmin() throws Exception {
			// given
			MockMultipartFile file = FileFixture.image();

			// when & then
			mockMvc.perform(multipart("/api/v1/files/images")
							.file(file)
							.header(HttpHeaders.AUTHORIZATION, userToken()))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(fileService, never()).uploadImage(any());
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// given
			MockMultipartFile file = FileFixture.image();

			// when & then
			mockMvc.perform(multipart("/api/v1/files/images").file(file))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(fileService, never()).uploadImage(any());
		}

		@Test
		@DisplayName("서비스가 형식 오류를 던지면 400 FILE_INVALID_FORMAT 을 반환한다")
		void returnsBadRequestWhenInvalidFormat() throws Exception {
			// given
			MockMultipartFile file = FileFixture.image();
			willThrow(new BusinessException(ErrorCode.FILE_INVALID_FORMAT)).given(fileService).uploadImage(any());

			// when & then
			mockMvc.perform(multipart("/api/v1/files/images")
							.file(file)
							.header(HttpHeaders.AUTHORIZATION, adminToken()))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("FILE_INVALID_FORMAT")));
		}

		@Test
		@DisplayName("file 파트가 없으면 400 COMMON_INVALID_INPUT 을 반환한다")
		void returnsBadRequestWhenPartMissing() throws Exception {
			// when & then
			mockMvc.perform(multipart("/api/v1/files/images")
							.header(HttpHeaders.AUTHORIZATION, adminToken()))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_INVALID_INPUT")));
			verify(fileService, never()).uploadImage(any());
		}
	}
}
