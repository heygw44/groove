package com.groove.recommend.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.auth.jwt.JwtProvider;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;
import com.groove.member.entity.MemberRole;
import com.groove.product.dto.ArtistResponse;
import com.groove.product.dto.GenreResponse;
import com.groove.recommend.dto.TasteProfileResponse;
import com.groove.recommend.dto.TasteProfileUpdateRequest;
import com.groove.recommend.entity.Decade;
import com.groove.recommend.service.TasteProfileService;

@WebMvcTest(TasteProfileController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
	JwtProvider.class})
@ActiveProfiles("test")
class TasteProfileControllerTest {

	private static final String PATH = "/api/v1/members/me/taste-profile";
	private static final Long MEMBER_ID = 1L;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JwtProvider jwtProvider;

	@MockitoBean
	private TasteProfileService tasteProfileService;

	private String bearer() {
		return "Bearer " + jwtProvider.createAccessToken(MEMBER_ID, MemberRole.USER);
	}

	private TasteProfileResponse response() {
		return new TasteProfileResponse(List.of(new GenreResponse(3L, "Jazz")),
				List.of(new ArtistResponse(12L, "John Coltrane", "존 콜트레인")),
				List.of(Decade.D1970), LocalDateTime.of(2026, 9, 5, 10, 0));
	}

	@Nested
	@DisplayName("GET /api/v1/members/me/taste-profile")
	class GetMyProfile {

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환하고 서비스는 호출되지 않는다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			mockMvc.perform(get(PATH))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));

			verify(tasteProfileService, never()).getMyProfile(any());
		}

		@Test
		@DisplayName("인증된 요청이면 200 과 취향 프로필을 반환한다")
		void returnsProfile() throws Exception {
			given(tasteProfileService.getMyProfile(MEMBER_ID)).willReturn(response());

			mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.genres[0].id", is(3)))
					.andExpect(jsonPath("$.data.artists[0].name", is("John Coltrane")))
					.andExpect(jsonPath("$.data.decades[0]", is("D1970")))
					.andExpect(jsonPath("$.data.updatedAt", is("2026-09-05T10:00:00")));
		}

		@Test
		@DisplayName("프로필이 없으면 404 RECOMMEND_PROFILE_NOT_FOUND 를 반환한다")
		void returnsNotFoundWhenProfileAbsent() throws Exception {
			willThrow(new BusinessException(ErrorCode.RECOMMEND_PROFILE_NOT_FOUND))
					.given(tasteProfileService).getMyProfile(MEMBER_ID);

			mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer()))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code", is("RECOMMEND_PROFILE_NOT_FOUND")));
		}
	}

	@Nested
	@DisplayName("PUT /api/v1/members/me/taste-profile")
	class Update {

		private ResultActions perform(Object body) throws Exception {
			return mockMvc.perform(put(PATH)
					.header(HttpHeaders.AUTHORIZATION, bearer())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(body)));
		}

		@Test
		@DisplayName("유효한 요청이면 200 과 교체된 취향 프로필을 반환한다")
		void updatesProfile() throws Exception {
			given(tasteProfileService.update(eq(MEMBER_ID), any())).willReturn(response());
			TasteProfileUpdateRequest request =
					new TasteProfileUpdateRequest(List.of(3L), List.of(12L), List.of(Decade.D1970));

			perform(request)
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.genres[0].id", is(3)));

			verify(tasteProfileService).update(eq(MEMBER_ID), any());
		}

		@ParameterizedTest
		@DisplayName("개수 제약이나 중복을 어기면 400 과 필드 에러를 반환한다")
		@MethodSource("com.groove.recommend.controller.TasteProfileControllerTest#invalidRequests")
		void returnsBadRequestWhenConstraintViolated(String field, Map<String, Object> body) throws Exception {
			perform(body)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")))
					.andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem(field)));

			verify(tasteProfileService, never()).update(any(), any());
		}

		@Test
		@DisplayName("정의되지 않은 연대 값이면 400 COMMON_INVALID_INPUT 을 반환한다")
		void returnsBadRequestWhenDecadeUnknown() throws Exception {
			perform(Map.of("genreIds", List.of(3), "artistIds", List.of(), "decades", List.of("D1950")))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_INVALID_INPUT")));

			verify(tasteProfileService, never()).update(any(), any());
		}
	}

	private static Stream<Arguments> invalidRequests() {
		return Stream.of(
				Arguments.of("genreIds",
						body(List.of(), List.of(), List.of())),
				Arguments.of("genreIds",
						body(List.of(1, 2, 3, 4, 5, 6), List.of(), List.of())),
				Arguments.of("artistIds",
						body(List.of(1), List.of(1, 2, 3, 4, 5, 6), List.of())),
				Arguments.of("decades",
						body(List.of(1), List.of(), List.of("D1960", "D1970", "D1980", "D1990"))),
				Arguments.of("genreIdsDistinct",
						body(List.of(1, 1), List.of(), List.of())));
	}

	private static Map<String, Object> body(List<?> genreIds, List<?> artistIds, List<?> decades) {
		return Map.of("genreIds", genreIds, "artistIds", artistIds, "decades", decades);
	}
}
