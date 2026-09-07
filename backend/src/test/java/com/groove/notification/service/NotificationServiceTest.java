package com.groove.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.NotificationFixture;
import com.groove.fixture.ProductFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.PageResponse;
import com.groove.member.entity.Member;
import com.groove.notification.dto.NotificationResponse;
import com.groove.notification.dto.NotificationSearchRequest;
import com.groove.notification.dto.UnreadCountResponse;
import com.groove.notification.entity.Notification;
import com.groove.notification.repository.NotificationRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

	private static final Long MEMBER_ID = 1L;
	private static final Long OTHER_MEMBER_ID = 2L;
	private static final Long NOTIFICATION_ID = 1000L;
	private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-07T09:00:00Z"),
			ZoneId.of("Asia/Seoul"));

	@Mock
	NotificationRepository notificationRepository;

	NotificationService notificationService;

	Member member;
	Member other;
	Product product;

	@BeforeEach
	void setUp() {
		notificationService = new NotificationService(notificationRepository, FIXED_CLOCK);
		member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
		other = MemberFixture.withId(MemberFixture.create("other@groove.com"), OTHER_MEMBER_ID);
		Artist artist = ArtistFixture.withId(1L);
		product = ProductFixture.withId(ProductFixture.create(artist), 100L);
	}

	@Nested
	@DisplayName("getMyNotifications()")
	class GetMyNotifications {

		@Test
		@DisplayName("unreadOnly 가 true 면 안 읽은 알림만 조회하는 리포지토리 메서드를 부른다")
		void callsUnreadOnlyRepositoryWhenUnreadOnlyTrue() {
			// given
			Notification notification = NotificationFixture.withId(NotificationFixture.forProduct(member, product),
					NOTIFICATION_ID);
			Page<Notification> page = new PageImpl<>(List.of(notification));
			given(notificationRepository.findAllByMemberIdAndReadAtIsNull(eq(MEMBER_ID), any())).willReturn(page);

			// when
			PageResponse<NotificationResponse> response = notificationService.getMyNotifications(MEMBER_ID,
					new NotificationSearchRequest(0, 20, true));

			// then
			assertThat(response.content()).hasSize(1);
			verify(notificationRepository).findAllByMemberIdAndReadAtIsNull(eq(MEMBER_ID), any());
			verify(notificationRepository, never()).findAllByMemberId(any(), any());
		}

		@Test
		@DisplayName("unreadOnly 가 false 면 전체 알림을 조회하는 리포지토리 메서드를 부른다")
		void callsAllRepositoryWhenUnreadOnlyFalse() {
			// given
			Notification notification = NotificationFixture.withId(NotificationFixture.forProduct(member, product),
					NOTIFICATION_ID);
			Page<Notification> page = new PageImpl<>(List.of(notification));
			given(notificationRepository.findAllByMemberId(eq(MEMBER_ID), any())).willReturn(page);

			// when
			PageResponse<NotificationResponse> response = notificationService.getMyNotifications(MEMBER_ID,
					new NotificationSearchRequest(0, 20, false));

			// then
			assertThat(response.content()).hasSize(1);
			verify(notificationRepository).findAllByMemberId(eq(MEMBER_ID), any());
			verify(notificationRepository, never()).findAllByMemberIdAndReadAtIsNull(any(), any());
		}
	}

	@Nested
	@DisplayName("getUnreadCount()")
	class GetUnreadCount {

		@Test
		@DisplayName("안 읽은 알림 개수를 반환한다")
		void returnsUnreadCount() {
			// given
			given(notificationRepository.countByMemberIdAndReadAtIsNull(MEMBER_ID)).willReturn(3L);

			// when
			UnreadCountResponse response = notificationService.getUnreadCount(MEMBER_ID);

			// then
			assertThat(response.count()).isEqualTo(3L);
		}
	}

	@Nested
	@DisplayName("markRead()")
	class MarkRead {

		@Test
		@DisplayName("본인 알림이면 읽음 처리한다")
		void marksReadWhenOwner() {
			// given
			Notification notification = NotificationFixture.withId(NotificationFixture.forProduct(member, product),
					NOTIFICATION_ID);
			given(notificationRepository.findById(NOTIFICATION_ID)).willReturn(Optional.of(notification));

			// when
			notificationService.markRead(MEMBER_ID, NOTIFICATION_ID);

			// then
			assertThat(notification.getReadAt()).isNotNull();
		}

		@Test
		@DisplayName("알림이 없으면 NOTIFICATION_NOT_FOUND 예외를 던진다")
		void throwsWhenNotFound() {
			// given
			given(notificationRepository.findById(NOTIFICATION_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> notificationService.markRead(MEMBER_ID, NOTIFICATION_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
		}

		@Test
		@DisplayName("본인 알림이 아니면 NOTIFICATION_FORBIDDEN 예외를 던진다")
		void throwsWhenNotOwner() {
			// given
			Notification notification = NotificationFixture.withId(NotificationFixture.forProduct(other, product),
					NOTIFICATION_ID);
			given(notificationRepository.findById(NOTIFICATION_ID)).willReturn(Optional.of(notification));

			// when & then
			assertThatThrownBy(() -> notificationService.markRead(MEMBER_ID, NOTIFICATION_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.NOTIFICATION_FORBIDDEN);
			assertThat(notification.getReadAt()).isNull();
		}
	}

	@Nested
	@DisplayName("markAllRead()")
	class MarkAllRead {

		@Test
		@DisplayName("리포지토리의 일괄 읽음 처리를 호출한다")
		void callsBulkUpdate() {
			// when
			notificationService.markAllRead(MEMBER_ID);

			// then
			verify(notificationRepository).markAllRead(eq(MEMBER_ID), any());
		}
	}
}
