package com.groove.notification.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.groove.notification.dto.NotificationResponse;
import com.groove.notification.dto.UnreadCountResponse;
import com.groove.notification.entity.NotificationType;
import com.groove.notification.service.NotificationService;

@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
	JwtProvider.class})
@ActiveProfiles("test")
class NotificationControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtProvider jwtProvider;

	@MockitoBean
	NotificationService notificationService;

	private String bearer() {
		return "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.USER);
	}

	private NotificationResponse restockResponse() {
		return new NotificationResponse(301L, NotificationType.RESTOCK, 501L, null, "Kind of Blue", null,
				LocalDateTime.now());
	}

	@Nested
	@DisplayName("GET /api/v1/members/me/notifications")
	class GetMyNotifications {

		@Test
		@DisplayName("인증된 요청이면 200 과 알림 목록을 반환하고 해당 없는 id 는 응답에서 빠진다")
		void returnsNotifications() throws Exception {
			// given
			PageResponse<NotificationResponse> page = PageResponse.of(List.of(restockResponse()), 0, 20, 1);
			given(notificationService.getMyNotifications(eq(1L), any())).willReturn(page);

			// when & then
			mockMvc.perform(get("/api/v1/members/me/notifications").header(HttpHeaders.AUTHORIZATION, bearer()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.content[0].id", is(301)))
					.andExpect(jsonPath("$.data.content[0].type", is("RESTOCK")))
					.andExpect(jsonPath("$.data.content[0].productId", is(501)))
					.andExpect(jsonPath("$.data.content[0].albumId").doesNotExist())
					.andExpect(jsonPath("$.data.content[0].readAt").doesNotExist());
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(get("/api/v1/members/me/notifications"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(notificationService, never()).getMyNotifications(any(), any());
		}

		@Test
		@DisplayName("size 가 100 을 초과하면 400 COMMON_VALIDATION_FAILED 를 반환한다")
		void returnsBadRequestWhenSizeExceedsLimit() throws Exception {
			// when & then
			mockMvc.perform(get("/api/v1/members/me/notifications")
							.header(HttpHeaders.AUTHORIZATION, bearer())
							.param("size", "101"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")));
		}
	}

	@Nested
	@DisplayName("GET /api/v1/members/me/notifications/unread-count")
	class GetUnreadCount {

		@Test
		@DisplayName("인증된 요청이면 200 과 안 읽은 개수를 반환한다")
		void returnsUnreadCount() throws Exception {
			// given
			given(notificationService.getUnreadCount(1L)).willReturn(UnreadCountResponse.of(3L));

			// when & then
			mockMvc.perform(get("/api/v1/members/me/notifications/unread-count")
							.header(HttpHeaders.AUTHORIZATION, bearer()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.count", is(3)));
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(get("/api/v1/members/me/notifications/unread-count"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
		}
	}

	@Nested
	@DisplayName("PATCH /api/v1/notifications/{id}/read")
	class MarkRead {

		@Test
		@DisplayName("본인 알림이면 200 을 반환하고 읽음 처리한다")
		void marksReadWhenOwner() throws Exception {
			// when & then
			mockMvc.perform(patch("/api/v1/notifications/301/read").header(HttpHeaders.AUTHORIZATION, bearer()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.success", is(true)))
					// ApiResponse 가 NON_NULL 직렬화라 data 는 null 이 아니라 키 자체가 빠진다.
					.andExpect(jsonPath("$.data").doesNotExist());
			verify(notificationService).markRead(1L, 301L);
		}

		@Test
		@DisplayName("존재하지 않는 알림이면 404 NOTIFICATION_NOT_FOUND 를 반환한다")
		void returnsNotFoundWhenNotificationMissing() throws Exception {
			// given
			willThrow(new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND))
					.given(notificationService).markRead(1L, 301L);

			// when & then
			mockMvc.perform(patch("/api/v1/notifications/301/read").header(HttpHeaders.AUTHORIZATION, bearer()))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code", is("NOTIFICATION_NOT_FOUND")));
		}

		@Test
		@DisplayName("본인 알림이 아니면 403 NOTIFICATION_FORBIDDEN 을 반환한다")
		void returnsForbiddenWhenNotOwner() throws Exception {
			// given
			willThrow(new BusinessException(ErrorCode.NOTIFICATION_FORBIDDEN))
					.given(notificationService).markRead(1L, 301L);

			// when & then
			mockMvc.perform(patch("/api/v1/notifications/301/read").header(HttpHeaders.AUTHORIZATION, bearer()))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("NOTIFICATION_FORBIDDEN")));
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(patch("/api/v1/notifications/301/read"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(notificationService, never()).markRead(any(), any());
		}
	}

	@Nested
	@DisplayName("PATCH /api/v1/members/me/notifications/read-all")
	class MarkAllRead {

		@Test
		@DisplayName("인증된 요청이면 200 을 반환하고 전체 읽음 처리한다")
		void marksAllRead() throws Exception {
			// when & then
			mockMvc.perform(patch("/api/v1/members/me/notifications/read-all")
							.header(HttpHeaders.AUTHORIZATION, bearer()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.success", is(true)));
			verify(notificationService).markAllRead(1L);
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(patch("/api/v1/members/me/notifications/read-all"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(notificationService, never()).markAllRead(any());
		}
	}
}
