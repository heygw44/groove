package com.groove.file;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.auth.jwt.JwtProvider;
import com.groove.member.entity.MemberRole;
import com.groove.support.IntegrationTestSupport;

@AutoConfigureMockMvc
class FileUploadIntegrationTest extends IntegrationTestSupport {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	JwtProvider jwtProvider;

	private String adminToken() {
		return "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.ADMIN);
	}

	@Nested
	@DisplayName("이미지 업로드 → 정적 서빙")
	class UploadAndServe {

		@Test
		@DisplayName("업로드에 성공하면 반환된 url 로 파일을 조회할 수 있다")
		void uploadsAndServesTheFile() throws Exception {
			// given
			byte[] content = "png-bytes".getBytes();
			MockMultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", content);

			// when
			MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/files/images")
							.file(file)
							.header(HttpHeaders.AUTHORIZATION, adminToken()))
					.andExpect(status().isOk())
					.andReturn();

			JsonNode body = objectMapper.readTree(uploadResult.getResponse().getContentAsString());
			String url = body.path("data").path("url").asText();
			String path = url.substring(url.indexOf("/uploads/"));

			// then
			mockMvc.perform(get(path))
					.andExpect(status().isOk());
		}

		@Test
		@DisplayName("일반 회원이면 403 을 반환한다")
		void returnsForbiddenWhenNotAdmin() throws Exception {
			// given
			byte[] content = "png-bytes".getBytes();
			MockMultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", content);
			String userToken = "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.USER);

			// when & then
			mockMvc.perform(multipart("/api/v1/files/images")
							.file(file)
							.header(HttpHeaders.AUTHORIZATION, userToken))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
		}
	}
}
