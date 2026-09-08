package com.groove.notification.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.groove.auth.jwt.JwtProvider;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.PageResponse;
import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;
import com.groove.member.entity.MemberRole;
import com.groove.notification.dto.AlbumWatchResponse;
import com.groove.notification.service.AlbumWatchService;

@WebMvcTest(AlbumWatchController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
	JwtProvider.class})
@ActiveProfiles("test")
class AlbumWatchControllerTest {

	private static final String BASE_URL = "/api/v1/members/me/album-watches";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtProvider jwtProvider;

	@MockitoBean
	AlbumWatchService albumWatchService;

	private String bearer() {
		return "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.USER);
	}

	private AlbumWatchResponse sampleResponse() {
		return new AlbumWatchResponse(12L, 100L, "A Love Supreme", LocalDateTime.now());
	}

	@Nested
	@DisplayName("POST /api/v1/members/me/album-watches/{albumId}")
	class Add {

		@Test
		@DisplayName("유효한 요청이면 201 과 등록된 구독을 반환한다")
		void addsAlbumWatch() throws Exception {
			// given
			given(albumWatchService.add(eq(1L), eq(100L))).willReturn(sampleResponse());

			// when & then
			mockMvc.perform(post(BASE_URL + "/100").header(HttpHeaders.AUTHORIZATION, bearer()))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.albumId", is(100)))
					.andExpect(jsonPath("$.data.albumTitle", is("A Love Supreme")));
			verify(albumWatchService).add(1L, 100L);
		}

		@Test
		@DisplayName("존재하지 않는 앨범이면 404 ALBUM_NOT_FOUND 를 반환한다")
		void returnsNotFoundWhenAlbumNotFound() throws Exception {
			// given
			willThrow(new BusinessException(ErrorCode.ALBUM_NOT_FOUND))
					.given(albumWatchService).add(eq(1L), eq(100L));

			// when & then
			mockMvc.perform(post(BASE_URL + "/100").header(HttpHeaders.AUTHORIZATION, bearer()))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code", is("ALBUM_NOT_FOUND")));
		}

		@Test
		@DisplayName("이미 구독 중이면 409 ALBUM_WATCH_ALREADY_EXISTS 를 반환한다")
		void returnsConflictWhenAlreadyWatching() throws Exception {
			// given
			willThrow(new BusinessException(ErrorCode.ALBUM_WATCH_ALREADY_EXISTS))
					.given(albumWatchService).add(eq(1L), eq(100L));

			// when & then
			mockMvc.perform(post(BASE_URL + "/100").header(HttpHeaders.AUTHORIZATION, bearer()))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error.code", is("ALBUM_WATCH_ALREADY_EXISTS")));
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(post(BASE_URL + "/100"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(albumWatchService, never()).add(any(), any());
		}
	}

	@Nested
	@DisplayName("DELETE /api/v1/members/me/album-watches/{albumId}")
	class Remove {

		@Test
		@DisplayName("구독 중이면 200 을 반환하고 해지를 처리한다")
		void removesAlbumWatch() throws Exception {
			// when & then
			mockMvc.perform(delete(BASE_URL + "/100").header(HttpHeaders.AUTHORIZATION, bearer()))
					.andExpect(status().isOk());
			verify(albumWatchService).remove(1L, 100L);
		}

		@Test
		@DisplayName("구독하지 않은 앨범이면 404 ALBUM_WATCH_NOT_FOUND 를 반환한다")
		void returnsNotFoundWhenNotWatching() throws Exception {
			// given
			willThrow(new BusinessException(ErrorCode.ALBUM_WATCH_NOT_FOUND))
					.given(albumWatchService).remove(1L, 100L);

			// when & then
			mockMvc.perform(delete(BASE_URL + "/100").header(HttpHeaders.AUTHORIZATION, bearer()))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code", is("ALBUM_WATCH_NOT_FOUND")));
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(delete(BASE_URL + "/100"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(albumWatchService, never()).remove(any(), any());
		}
	}

	@Nested
	@DisplayName("GET /api/v1/members/me/album-watches")
	class GetMyWatches {

		@Test
		@DisplayName("인증된 요청이면 200 과 구독 목록을 반환한다")
		void returnsMyWatches() throws Exception {
			// given
			given(albumWatchService.getMyWatches(eq(1L), any()))
					.willReturn(PageResponse.of(List.of(sampleResponse()), 0, 20, 1));

			// when & then
			mockMvc.perform(get(BASE_URL).header(HttpHeaders.AUTHORIZATION, bearer()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.content[0].albumId", is(100)));
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(get(BASE_URL))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(albumWatchService, never()).getMyWatches(any(), any());
		}

		@Test
		@DisplayName("size 가 100 을 초과하면 400 COMMON_VALIDATION_FAILED 를 반환한다")
		void returnsBadRequestWhenSizeExceedsLimit() throws Exception {
			// when & then
			mockMvc.perform(get(BASE_URL).header(HttpHeaders.AUTHORIZATION, bearer()).param("size", "101"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")));
			verify(albumWatchService, never()).getMyWatches(any(), any());
		}
	}
}
