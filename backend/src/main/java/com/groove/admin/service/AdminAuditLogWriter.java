package com.groove.admin.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.groove.admin.entity.AdminAuditLog;
import com.groove.admin.repository.AdminAuditLogRepository;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;

import lombok.extern.slf4j.Slf4j;

/** {@link AdminAuditEvent} 를 커밋 이후 별도 트랜잭션으로 저장한다. */
@Slf4j
@Component
public class AdminAuditLogWriter {

	private final AdminAuditLogRepository adminAuditLogRepository;
	private final MemberRepository memberRepository;
	private final TransactionTemplate transactionTemplate;

	public AdminAuditLogWriter(AdminAuditLogRepository adminAuditLogRepository, MemberRepository memberRepository,
			PlatformTransactionManager transactionManager) {
		this.adminAuditLogRepository = adminAuditLogRepository;
		this.memberRepository = memberRepository;
		DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
		definition.setPropagationBehavior(DefaultTransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.transactionTemplate = new TransactionTemplate(transactionManager, definition);
	}

	// AFTER_COMMIT 시점엔 원래 트랜잭션이 이미 커밋되어 새 트랜잭션이 필요하다.
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void handle(AdminAuditEvent event) {
		try {
			transactionTemplate.executeWithoutResult(status -> {
				Member admin = memberRepository.getReferenceById(event.adminId());
				adminAuditLogRepository.save(AdminAuditLog.record(admin, event.action(), event.targetType(),
						event.targetId(), event.detail(), event.ipAddress()));
			});
		} catch (RuntimeException e) {
			// afterCommit 에서 던진 예외는 호출자에게 전파되므로 감사 로그 저장 실패는 여기서 삼킨다.
			log.error("감사 로그 저장 실패: adminId={}, action={}", event.adminId(), event.action(), e);
		}
	}
}
