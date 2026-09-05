package com.groove.admin.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.groove.admin.dto.AdminAuditLogResponse;
import com.groove.admin.dto.AdminAuditLogSearchCondition;
import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditLog;
import com.groove.admin.entity.AdminAuditTargetType;
import com.groove.fixture.MemberFixture;
import com.groove.member.entity.Member;
import com.groove.support.MybatisTestSupport;

import jakarta.persistence.EntityManager;

/** 공유 테스트 DB 에 다른 테스트가 남긴 감사 로그가 섞이므로 자기 admin id 로만 단언한다. */
class AdminAuditLogQueryMapperTest extends MybatisTestSupport {

	@Autowired
	private AdminAuditLogQueryMapper adminAuditLogQueryMapper;

	@Autowired
	private EntityManager em;

	private Member admin;
	private Member otherAdmin;

	@BeforeEach
	void setUp() {
		admin = MemberFixture.createAdmin("aalqm-admin@groove.com");
		otherAdmin = MemberFixture.createAdmin("aalqm-other-admin@groove.com");
		em.persist(admin);
		em.persist(otherAdmin);
	}

	private AdminAuditLog persistLog(Member admin, AdminAuditAction action, AdminAuditTargetType targetType,
			Long targetId) {
		AdminAuditLog log = AdminAuditLog.record(admin, action, targetType, targetId, "detail", "203.0.113.7");
		em.persist(log);
		em.flush();
		return log;
	}

	private static AdminAuditLogSearchCondition condition(AdminAuditAction action, AdminAuditTargetType targetType,
			Long adminId, LocalDateTime fromAt, LocalDateTime toExclusiveAt) {
		return new AdminAuditLogSearchCondition(action, targetType, adminId, fromAt, toExclusiveAt, 0, 20);
	}

	@Nested
	@DisplayName("findAuditLogs()")
	class FindAuditLogs {

		@Test
		@DisplayName("action 으로 필터링하면 해당 행위 로그만 반환한다")
		void filtersByAction() {
			// given
			AdminAuditLog stockLog = persistLog(admin, AdminAuditAction.STOCK_ADJUST, AdminAuditTargetType.PRODUCT,
					1L);
			persistLog(admin, AdminAuditAction.PRODUCT_HIDE, AdminAuditTargetType.PRODUCT, 1L);
			em.clear();

			// when
			List<AdminAuditLogResponse> result = adminAuditLogQueryMapper.findAuditLogs(
					condition(AdminAuditAction.STOCK_ADJUST, null, admin.getId(), null, null));

			// then
			assertThat(result).extracting(AdminAuditLogResponse::id).containsExactly(stockLog.getId());
		}

		@Test
		@DisplayName("targetType 으로 필터링하면 해당 대상 유형 로그만 반환한다")
		void filtersByTargetType() {
			// given
			AdminAuditLog paymentLog = persistLog(admin, AdminAuditAction.PAYMENT_CANCEL,
					AdminAuditTargetType.PAYMENT, 1L);
			persistLog(admin, AdminAuditAction.ORDER_STATUS_CHANGE, AdminAuditTargetType.ORDER, 1L);
			em.clear();

			// when
			List<AdminAuditLogResponse> result = adminAuditLogQueryMapper.findAuditLogs(
					condition(null, AdminAuditTargetType.PAYMENT, admin.getId(), null, null));

			// then
			assertThat(result).extracting(AdminAuditLogResponse::id).containsExactly(paymentLog.getId());
		}

		@Test
		@DisplayName("adminId 로 필터링하면 다른 관리자의 로그는 제외한다")
		void filtersByAdminId() {
			// given
			AdminAuditLog ownLog = persistLog(admin, AdminAuditAction.STOCK_ADJUST, AdminAuditTargetType.PRODUCT, 1L);
			persistLog(otherAdmin, AdminAuditAction.STOCK_ADJUST, AdminAuditTargetType.PRODUCT, 1L);
			em.clear();

			// when
			List<AdminAuditLogResponse> result = adminAuditLogQueryMapper.findAuditLogs(
					condition(null, null, admin.getId(), null, null));

			// then
			assertThat(result).extracting(AdminAuditLogResponse::id).containsExactly(ownLog.getId());
		}

		@Test
		@DisplayName("기간으로 필터링하면 경계값을 포함해 반환한다")
		void filtersByPeriodInclusiveOfBoundaries() {
			// given
			AdminAuditLog log = persistLog(admin, AdminAuditAction.STOCK_ADJUST, AdminAuditTargetType.PRODUCT, 1L);
			em.clear();
			LocalDateTime createdAt = em.find(AdminAuditLog.class, log.getId()).getCreatedAt();

			// when
			List<AdminAuditLogResponse> inRange = adminAuditLogQueryMapper.findAuditLogs(
					condition(null, null, admin.getId(), createdAt, createdAt.plusSeconds(1)));
			List<AdminAuditLogResponse> beforeRange = adminAuditLogQueryMapper.findAuditLogs(
					condition(null, null, admin.getId(), createdAt.plusSeconds(1), null));

			// then
			assertThat(inRange).extracting(AdminAuditLogResponse::id).contains(log.getId());
			assertThat(beforeRange).extracting(AdminAuditLogResponse::id).doesNotContain(log.getId());
		}

		@Test
		@DisplayName("최신순(created_at DESC, id DESC)으로 정렬하고 관리자 닉네임을 함께 반환한다")
		void ordersByCreatedAtDescAndJoinsNickname() {
			// given
			AdminAuditLog first = persistLog(admin, AdminAuditAction.STOCK_ADJUST, AdminAuditTargetType.PRODUCT, 1L);
			AdminAuditLog second = persistLog(admin, AdminAuditAction.STOCK_ADJUST, AdminAuditTargetType.PRODUCT, 2L);
			em.clear();

			// when
			List<AdminAuditLogResponse> result = adminAuditLogQueryMapper.findAuditLogs(
					condition(null, null, admin.getId(), null, null));

			// then
			assertThat(result).extracting(AdminAuditLogResponse::id)
					.containsExactly(second.getId(), first.getId());
			assertThat(result).extracting(AdminAuditLogResponse::adminNickname).containsOnly(admin.getNickname());
		}
	}

	@Nested
	@DisplayName("countAuditLogs()")
	class CountAuditLogs {

		@Test
		@DisplayName("조건에 맞는 로그 건수를 반환한다")
		void countsMatchingLogs() {
			// given
			persistLog(admin, AdminAuditAction.STOCK_ADJUST, AdminAuditTargetType.PRODUCT, 1L);
			persistLog(admin, AdminAuditAction.STOCK_ADJUST, AdminAuditTargetType.PRODUCT, 2L);
			persistLog(admin, AdminAuditAction.PRODUCT_HIDE, AdminAuditTargetType.PRODUCT, 1L);
			em.clear();

			// when
			long count = adminAuditLogQueryMapper.countAuditLogs(
					condition(AdminAuditAction.STOCK_ADJUST, null, admin.getId(), null, null));

			// then
			assertThat(count).isEqualTo(2);
		}
	}
}
