package com.groove.claim.application;

import com.groove.claim.domain.Claim;
import com.groove.claim.domain.ClaimRepository;
import com.groove.claim.domain.ClaimStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 반품 자동 진행 스케줄러. 단계별 delay 이상 머문 반품을 다음 상태로 민다.
 * 상태 전이는 ClaimService 트랜잭션 메서드에 위임하고 건별로 격리(한 건 실패가 나머지를 막지 않게).
 */
@Component
public class ClaimProgressScheduler {

    private static final Logger log = LoggerFactory.getLogger(ClaimProgressScheduler.class);

    private final ClaimRepository claimRepository;
    private final ClaimService claimService;
    private final Clock clock;
    private final Duration approveDelay;
    private final Duration transitDelay;
    private final Duration inspectDelay;
    private final Limit batchLimit;

    public ClaimProgressScheduler(ClaimRepository claimRepository,
                                  ClaimService claimService,
                                  Clock clock,
                                  @Value("${groove.claim.progress.approve-delay:PT5S}") Duration approveDelay,
                                  @Value("${groove.claim.progress.transit-delay:PT5S}") Duration transitDelay,
                                  @Value("${groove.claim.progress.inspect-delay:PT5S}") Duration inspectDelay,
                                  @Value("${groove.claim.progress.batch-size:200}") int batchSize) {
        this.claimRepository = claimRepository;
        this.claimService = claimService;
        this.clock = clock;
        this.approveDelay = Objects.requireNonNull(approveDelay, "approveDelay");
        this.transitDelay = Objects.requireNonNull(transitDelay, "transitDelay");
        this.inspectDelay = Objects.requireNonNull(inspectDelay, "inspectDelay");
        if (batchSize <= 0) {
            throw new IllegalArgumentException("groove.claim.progress.batch-size 는 양수여야 합니다: " + batchSize);
        }
        this.batchLimit = Limit.of(batchSize);
    }

    @Scheduled(
            fixedDelayString = "${groove.claim.progress.interval:PT2S}",
            initialDelayString = "${groove.claim.progress.initial-delay:PT5S}")
    public void progressClaims() {
        Instant now = clock.instant();
        advance("APPROVED→IN_TRANSIT",
                claimRepository.findByStatusAndApprovedAtBeforeOrderByApprovedAtAscIdAsc(
                        ClaimStatus.APPROVED, now.minus(approveDelay), batchLimit),
                claim -> claimService.advanceToInTransit(claim.getId()));
        advance("IN_TRANSIT→INSPECTING",
                claimRepository.findByStatusAndInTransitAtBeforeOrderByInTransitAtAscIdAsc(
                        ClaimStatus.IN_TRANSIT, now.minus(transitDelay), batchLimit),
                claim -> claimService.advanceToInspecting(claim.getId()));
        advance("INSPECTING→REFUNDED",
                claimRepository.findByStatusAndInspectingAtBeforeOrderByInspectingAtAscIdAsc(
                        ClaimStatus.INSPECTING, now.minus(inspectDelay), batchLimit),
                claim -> claimService.completeRefund(claim.getId()));
    }

    private void advance(String step, List<Claim> candidates, Consumer<Claim> transition) {
        if (candidates.isEmpty()) {
            return;
        }
        log.debug("반품 자동 진행 {} 대상 {}건 (limit={})", step, candidates.size(), batchLimit.max());
        for (Claim claim : candidates) {
            try {
                transition.accept(claim);
            } catch (RuntimeException e) {
                log.warn("반품 자동 진행 실패: {} claimId={} — 다음 주기에 재시도", step, claim.getId(), e);
            }
        }
    }
}
