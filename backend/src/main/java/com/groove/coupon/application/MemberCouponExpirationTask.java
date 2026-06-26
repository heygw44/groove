package com.groove.coupon.application;

import com.groove.common.transaction.CommonTransactionConfig;
import com.groove.coupon.domain.MemberCouponRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;

/**
 * 회원 보유 쿠폰 만료 배치. groove.coupon.expiration.cron 으로 주기 실행(기본 매시 정각), expires_at 이 지난 ISSUED 행을
 * batch-size 개씩 독립 트랜잭션으로 EXPIRED 로 전환한다. 벌크 UPDATE 는 MemberCoupon.expire() 를 우회한다.
 * 배치 실패는 로깅만 하고 다음 주기에 재시도한다.
 */
@Component
public class MemberCouponExpirationTask {

    private static final Logger log = LoggerFactory.getLogger(MemberCouponExpirationTask.class);

    private final MemberCouponRepository repository;
    private final TransactionTemplate requiresNewTx;
    private final Clock clock;
    private final int batchSize;

    public MemberCouponExpirationTask(
            MemberCouponRepository repository,
            @Qualifier(CommonTransactionConfig.REQUIRES_NEW_TX_TEMPLATE) TransactionTemplate requiresNewTx,
            Clock clock,
            CouponExpirationProperties properties) {
        this.repository = repository;
        this.requiresNewTx = requiresNewTx;
        this.clock = clock;
        this.batchSize = properties.batchSize();
    }

    @Scheduled(cron = "${groove.coupon.expiration.cron:0 0 * * * *}")
    public void expireOverdue() {
        try {
            int total = expireOverdueAll(clock.instant());
            if (total > 0) {
                log.info("만료된 회원 쿠폰 {}건 EXPIRED 전환", total);
            }
        } catch (Exception e) {
            // 스케줄러 스레드가 죽지 않도록 Exception 만 잡고 Error 는 전파시킨다. 다음 주기에 재시도.
            log.warn("회원 쿠폰 만료 처리 실패 — 다음 주기에 재시도", e);
        }
    }

    /**
     * expires_at < cutoff 인 ISSUED 회원 쿠폰을 모두 EXPIRED 로 전환한다. 영향 행 수가 배치 크기 미만이 될
     * 때까지 배치 단위로 반복하고, 전환된 총 행 수를 반환한다.
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
