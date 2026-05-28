package com.groove.coupon.application;

import com.groove.common.transaction.CommonTransactionConfig;
import com.groove.coupon.domain.MemberCouponRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

/**
 * 회원 보유 쿠폰 만료 배치 (이슈 #92 DoD).
 *
 * <p>{@code groove.coupon.expiration.cron} cron 으로 주기 실행되며(기본 매시 정각), {@code expires_at}
 * 이 지난 {@code ISSUED} 행을 {@code groove.coupon.expiration.batch-size} 개씩 독립 트랜잭션으로
 * {@code EXPIRED} 로 전환한다 — {@code IdempotencyRecordCleanupTask} 와 동일 구조. cron 을 {@code "-"}
 * 로 두면 비활성화된다 (테스트 프로파일에서 사용).
 *
 * <p>USED/CANCELLED 는 종착 상태라 WHERE 조건에서 자동 제외된다. 벌크 UPDATE 는 도메인 메서드
 * {@code MemberCoupon.expire()} 를 우회하지만, {@code MemberCouponStatus.canTransitionTo} 가
 * {@code ISSUED → EXPIRED} 를 허용하므로 도메인 규칙과 정합한다 — 도메인 가드는 단건 경로의 백스톱으로
 * 남는다.
 *
 * <p>스케줄러 스레드에서 예외가 새어 나가면 다음 실행에 영향을 주므로, 배치 실패는 잡아서 로깅만 한다 —
 * 다음 주기에 재시도된다.
 */
@Component
public class MemberCouponExpirationTask {

    private static final Logger log = LoggerFactory.getLogger(MemberCouponExpirationTask.class);

    private final MemberCouponRepository repository;
    private final TransactionTemplate requiresNewTx;
    private final int batchSize;

    public MemberCouponExpirationTask(
            MemberCouponRepository repository,
            @Qualifier(CommonTransactionConfig.REQUIRES_NEW_TX_TEMPLATE) TransactionTemplate requiresNewTx,
            CouponExpirationProperties properties) {
        this.repository = repository;
        this.requiresNewTx = requiresNewTx;
        this.batchSize = properties.batchSize();
    }

    @Scheduled(cron = "${groove.coupon.expiration.cron:0 0 * * * *}")
    public void expireOverdue() {
        try {
            int total = expireOverdueAll(Instant.now());
            if (total > 0) {
                log.info("만료된 회원 쿠폰 {}건 EXPIRED 전환", total);
            }
        } catch (Throwable e) {
            // Throwable 까지 잡아 스케줄러 스레드가 죽지 않도록 보호 (#92 리뷰). 다음 주기에 재시도.
            log.warn("회원 쿠폰 만료 처리 실패 — 다음 주기에 재시도", e);
        }
    }

    /**
     * {@code expires_at < cutoff} 인 ISSUED 회원 쿠폰을 모두 EXPIRED 로 전환한다. {@code cutoff} 고정값
     * 기준이라 대상 집합은 유한하며, 배치 단위로 영향 행 수가 배치 크기 미만이 될 때까지 반복한다.
     *
     * @return 전환된 총 행 수
     */
    int expireOverdueAll(Instant cutoff) {
        int total = 0;
        int updated;
        do {
            Integer batch = requiresNewTx.execute(status -> repository.expireOverdueBatch(cutoff, batchSize));
            updated = batch == null ? 0 : batch;
            total += updated;
        } while (updated == batchSize);
        return total;
    }
}
