package com.groove.stats.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.member.entity.Member;
import com.groove.member.entity.MemberRole;
import com.groove.member.repository.MemberRepository;
import com.groove.notification.entity.Notification;
import com.groove.notification.entity.NotificationType;
import com.groove.notification.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 대사 불일치를 관리자에게 알린다. {@code NotificationDispatcher}(알림 도메인)는 위시리스트·앨범 구독 기반
 * 수신자 해석이라 이 알림(ROLE_ADMIN 전원 대상)과 성격이 달라 재사용하지 않는다.
 *
 * <p>1차 채널은 로그다 — 운영에서 실제로 보는 건 이쪽이고 알림은 보조 채널이다.</p>
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class StatsAlertDispatcher {

	private static final String TITLE_FORMAT = "매출 대사 불일치 %d일 발생 (%s ~ %s)";

	private final MemberRepository memberRepository;
	private final NotificationRepository notificationRepository;

	/** 대사 실행(최대 35일) 한 번당 알림 한 건으로 묶는다. 지표 개수만큼 쌓으면 관리자 알림함이 실행 한 번에 도배된다. */
	@Transactional
	public void dispatchMismatchSummary(LocalDate from, LocalDate to, int unresolvedDateCount) {
		log.error("매출 대사 불일치 실행 요약 range={}~{} unresolvedDates={}", from, to, unresolvedDateCount);

		List<Member> admins = memberRepository.findAllByRole(MemberRole.ADMIN);
		if (admins.isEmpty()) {
			return;
		}

		String title = String.format(TITLE_FORMAT, unresolvedDateCount, from, to);
		List<Notification> notifications = admins.stream()
				.map(admin -> Notification.forSystem(admin, NotificationType.STATS_MISMATCH, title))
				.toList();
		notificationRepository.saveAll(notifications);
	}
}
