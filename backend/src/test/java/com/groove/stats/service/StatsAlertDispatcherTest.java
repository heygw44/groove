package com.groove.stats.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.fixture.MemberFixture;
import com.groove.member.entity.Member;
import com.groove.member.entity.MemberRole;
import com.groove.member.repository.MemberRepository;
import com.groove.notification.entity.Notification;
import com.groove.notification.entity.NotificationType;
import com.groove.notification.repository.NotificationRepository;

@ExtendWith(MockitoExtension.class)
class StatsAlertDispatcherTest {

	private static final LocalDate FROM = LocalDate.of(2031, 3, 1);
	private static final LocalDate TO = LocalDate.of(2031, 4, 4);

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private NotificationRepository notificationRepository;

	private StatsAlertDispatcher statsAlertDispatcher;

	@BeforeEach
	void setUp() {
		statsAlertDispatcher = new StatsAlertDispatcher(memberRepository, notificationRepository);
	}

	@Nested
	@DisplayName("dispatchMismatchSummary()")
	class DispatchMismatchSummary {

		@Test
		@DisplayName("관리자 전원에게 실행 하나당 알림 하나씩만 적재한다")
		void savesOneNotificationPerAdmin() {
			// given
			Member admin1 = MemberFixture.createAdmin("admin1@groove.com");
			Member admin2 = MemberFixture.createAdmin("admin2@groove.com");
			given(memberRepository.findAllByRole(MemberRole.ADMIN)).willReturn(List.of(admin1, admin2));

			// when
			statsAlertDispatcher.dispatchMismatchSummary(FROM, TO, 3);

			// then
			ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
			verify(notificationRepository).saveAll(captor.capture());
			List<Notification> notifications = captor.getValue();
			assertThat(notifications).hasSize(2);
			assertThat(notifications).allMatch(n -> n.getType() == NotificationType.STATS_MISMATCH);
			assertThat(notifications).allMatch(n -> n.getTitleSnapshot().contains("3"));
		}

		@Test
		@DisplayName("관리자가 없으면 알림을 적재하지 않는다")
		void doesNothingWhenNoAdmins() {
			// given
			given(memberRepository.findAllByRole(MemberRole.ADMIN)).willReturn(List.of());

			// when
			statsAlertDispatcher.dispatchMismatchSummary(FROM, TO, 1);

			// then
			verify(notificationRepository, never()).saveAll(anyList());
		}
	}
}
