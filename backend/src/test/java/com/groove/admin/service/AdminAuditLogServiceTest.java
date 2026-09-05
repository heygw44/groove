package com.groove.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.context.ApplicationEventPublisher;

import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditTargetType;

@ExtendWith(MockitoExtension.class)
class AdminAuditLogServiceTest {

	private static final Long ADMIN_ID = 1L;

	@Mock
	ApplicationEventPublisher eventPublisher;

	@Mock
	ClientIpResolver clientIpResolver;

	AdminAuditLogService adminAuditLogService;

	@BeforeEach
	void setUp() {
		adminAuditLogService = new AdminAuditLogService(eventPublisher, clientIpResolver);
	}

	@Nested
	@DisplayName("record()")
	class Record {

		@Test
		@DisplayName("해석된 IP 를 포함한 감사 로그 이벤트를 발행한다")
		void publishesEventWithResolvedIp() {
			// given
			given(clientIpResolver.resolve()).willReturn("203.0.113.7");

			// when
			adminAuditLogService.record(ADMIN_ID, AdminAuditAction.PRODUCT_CREATE, AdminAuditTargetType.PRODUCT, 10L,
					"detail");

			// then
			ArgumentCaptor<AdminAuditEvent> captor = ArgumentCaptor.forClass(AdminAuditEvent.class);
			verify(eventPublisher).publishEvent(captor.capture());
			AdminAuditEvent event = captor.getValue();
			assertThat(event.adminId()).isEqualTo(ADMIN_ID);
			assertThat(event.action()).isEqualTo(AdminAuditAction.PRODUCT_CREATE);
			assertThat(event.targetType()).isEqualTo(AdminAuditTargetType.PRODUCT);
			assertThat(event.targetId()).isEqualTo(10L);
			assertThat(event.detail()).isEqualTo("detail");
			assertThat(event.ipAddress()).isEqualTo("203.0.113.7");
		}

		@Test
		@DisplayName("detail 이 null 이어도 이벤트를 발행한다")
		void publishesEventWithNullDetail() {
			// given
			given(clientIpResolver.resolve()).willReturn("203.0.113.7");

			// when
			adminAuditLogService.record(ADMIN_ID, AdminAuditAction.PRODUCT_HIDE, AdminAuditTargetType.PRODUCT, 10L,
					null);

			// then
			ArgumentCaptor<AdminAuditEvent> captor = ArgumentCaptor.forClass(AdminAuditEvent.class);
			verify(eventPublisher).publishEvent(captor.capture());
			assertThat(captor.getValue().detail()).isNull();
		}

		@Test
		@DisplayName("IP 를 확인할 수 없으면 null 인 채로 이벤트를 발행한다")
		void publishesEventWithNullIpWhenUnresolved() {
			// given
			given(clientIpResolver.resolve()).willReturn(null);

			// when
			adminAuditLogService.record(ADMIN_ID, AdminAuditAction.PRODUCT_UPDATE, AdminAuditTargetType.PRODUCT, 10L,
					"title");

			// then
			ArgumentCaptor<AdminAuditEvent> captor = ArgumentCaptor.forClass(AdminAuditEvent.class);
			verify(eventPublisher).publishEvent(captor.capture());
			assertThat(captor.getValue().ipAddress()).isNull();
		}
	}
}
