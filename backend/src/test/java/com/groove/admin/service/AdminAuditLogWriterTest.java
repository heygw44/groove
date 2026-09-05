package com.groove.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditLog;
import com.groove.admin.entity.AdminAuditTargetType;
import com.groove.admin.repository.AdminAuditLogRepository;
import com.groove.fixture.MemberFixture;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;

@ExtendWith(MockitoExtension.class)
class AdminAuditLogWriterTest {

	private static final Long ADMIN_ID = 1L;

	@Mock
	AdminAuditLogRepository adminAuditLogRepository;

	@Mock
	MemberRepository memberRepository;

	@Mock
	PlatformTransactionManager transactionManager;

	@Mock
	TransactionStatus transactionStatus;

	AdminAuditLogWriter adminAuditLogWriter;

	@BeforeEach
	void setUp() {
		given(transactionManager.getTransaction(any())).willReturn(transactionStatus);
		adminAuditLogWriter = new AdminAuditLogWriter(adminAuditLogRepository, memberRepository, transactionManager);
	}

	@Nested
	@DisplayName("handle()")
	class Handle {

		@Test
		@DisplayName("REQUIRES_NEW 트랜잭션에서 관리자 참조와 함께 감사 로그를 저장한다")
		void savesAuditLogInNewTransaction() {
			// given
			Member admin = MemberFixture.withId(MemberFixture.create(), ADMIN_ID);
			given(memberRepository.getReferenceById(ADMIN_ID)).willReturn(admin);
			AdminAuditEvent event = new AdminAuditEvent(ADMIN_ID, AdminAuditAction.STOCK_ADJUST,
					AdminAuditTargetType.PRODUCT, 10L, "IN:10->15", "203.0.113.7");

			// when
			adminAuditLogWriter.handle(event);

			// then
			ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
			verify(adminAuditLogRepository).save(captor.capture());
			verify(transactionManager).commit(transactionStatus);
			AdminAuditLog saved = captor.getValue();
			assertThat(saved.getAdmin()).isEqualTo(admin);
			assertThat(saved.getAction()).isEqualTo(AdminAuditAction.STOCK_ADJUST);
			assertThat(saved.getTargetType()).isEqualTo(AdminAuditTargetType.PRODUCT);
			assertThat(saved.getTargetId()).isEqualTo(10L);
			assertThat(saved.getDetail()).isEqualTo("IN:10->15");
			assertThat(saved.getIpAddress()).isEqualTo("203.0.113.7");
		}

		@Test
		@DisplayName("저장에 실패해도 예외를 삼키고 롤백한다")
		void swallowsExceptionWhenSaveFails() {
			// given
			Member admin = MemberFixture.withId(MemberFixture.create(), ADMIN_ID);
			given(memberRepository.getReferenceById(ADMIN_ID)).willReturn(admin);
			willThrow(new RuntimeException("boom")).given(adminAuditLogRepository).save(any());
			AdminAuditEvent event = new AdminAuditEvent(ADMIN_ID, AdminAuditAction.STOCK_ADJUST,
					AdminAuditTargetType.PRODUCT, 10L, "IN:10->15", "203.0.113.7");

			// when & then
			assertThatCode(() -> adminAuditLogWriter.handle(event)).doesNotThrowAnyException();
			verify(transactionManager).rollback(transactionStatus);
		}
	}
}
