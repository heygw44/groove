package com.groove.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditLog;
import com.groove.admin.entity.AdminAuditTargetType;
import com.groove.admin.repository.AdminAuditLogRepository;
import com.groove.fixture.MemberFixture;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;

@ExtendWith(MockitoExtension.class)
class AdminAuditLogServiceTest {

	private static final Long ADMIN_ID = 1L;

	@Mock
	AdminAuditLogRepository adminAuditLogRepository;

	@Mock
	MemberRepository memberRepository;

	AdminAuditLogService adminAuditLogService;

	@BeforeEach
	void setUp() {
		adminAuditLogService = new AdminAuditLogService(adminAuditLogRepository, memberRepository);
	}

	@Nested
	@DisplayName("record()")
	class Record {

		@Test
		@DisplayName("프록시 참조로 조회한 관리자와 함께 감사 로그를 저장한다")
		void savesAuditLogWithAdminReference() {
			// given
			Member admin = MemberFixture.withId(MemberFixture.create(), ADMIN_ID);
			given(memberRepository.getReferenceById(ADMIN_ID)).willReturn(admin);

			// when
			adminAuditLogService.record(ADMIN_ID, AdminAuditAction.PRODUCT_CREATE, AdminAuditTargetType.PRODUCT, 10L,
					"detail");

			// then
			ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
			verify(adminAuditLogRepository).save(captor.capture());
			assertThat(captor.getValue().getAdmin()).isEqualTo(admin);
			assertThat(captor.getValue().getAction()).isEqualTo(AdminAuditAction.PRODUCT_CREATE);
			assertThat(captor.getValue().getTargetType()).isEqualTo(AdminAuditTargetType.PRODUCT);
			assertThat(captor.getValue().getTargetId()).isEqualTo(10L);
			assertThat(captor.getValue().getDetail()).isEqualTo("detail");
		}

		@Test
		@DisplayName("detail 이 null 이어도 저장한다")
		void savesAuditLogWithNullDetail() {
			// given
			Member admin = MemberFixture.withId(MemberFixture.create(), ADMIN_ID);
			given(memberRepository.getReferenceById(ADMIN_ID)).willReturn(admin);

			// when
			adminAuditLogService.record(ADMIN_ID, AdminAuditAction.PRODUCT_HIDE, AdminAuditTargetType.PRODUCT, 10L,
					null);

			// then
			ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
			verify(adminAuditLogRepository).save(captor.capture());
			assertThat(captor.getValue().getDetail()).isNull();
		}

		@Test
		@DisplayName("관리자 id 로 프록시 참조를 조회한다")
		void resolvesAdminByReference() {
			// given
			Member admin = MemberFixture.withId(MemberFixture.create(), ADMIN_ID);
			given(memberRepository.getReferenceById(ADMIN_ID)).willReturn(admin);

			// when
			adminAuditLogService.record(ADMIN_ID, AdminAuditAction.PRODUCT_UPDATE, AdminAuditTargetType.PRODUCT, 10L,
					"title");

			// then
			verify(memberRepository).getReferenceById(ADMIN_ID);
			verify(adminAuditLogRepository).save(any());
		}
	}
}
