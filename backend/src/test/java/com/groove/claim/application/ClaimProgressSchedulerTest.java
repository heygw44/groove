package com.groove.claim.application;

import com.groove.claim.domain.Claim;
import com.groove.claim.domain.ClaimRepository;
import com.groove.claim.domain.ClaimStatus;
import com.groove.order.domain.Order;
import com.groove.support.OrderFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClaimProgressScheduler — 자동 진행 (틱당 1단계)")
class ClaimProgressSchedulerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-12T00:00:00Z"), ZoneOffset.UTC);
    private static final Duration DELAY = Duration.ofSeconds(5);

    @Mock
    private ClaimRepository claimRepository;
    @Mock
    private ClaimService claimService;

    private ClaimProgressScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ClaimProgressScheduler(claimRepository, claimService, CLOCK, DELAY, DELAY, DELAY, 200);
    }

    private Claim claimWithId(long id) {
        Order order = OrderFixtures.memberOrder("ORD-" + id, id);
        Claim claim = Claim.request(order, "변심");
        ReflectionTestUtils.setField(claim, "id", id);
        return claim;
    }

    @Test
    @DisplayName("각 단계 대상을 advanceToInTransit/advanceToInspecting/completeRefund 로 한 단계씩 위임")
    void advancesEachCandidateOneStep() {
        given(claimRepository.findByStatusAndApprovedAtBefore(eq(ClaimStatus.APPROVED), any(Instant.class), any(Limit.class)))
                .willReturn(List.of(claimWithId(1L)));
        given(claimRepository.findByStatusAndInTransitAtBefore(eq(ClaimStatus.IN_TRANSIT), any(Instant.class), any(Limit.class)))
                .willReturn(List.of(claimWithId(2L)));
        given(claimRepository.findByStatusAndInspectingAtBefore(eq(ClaimStatus.INSPECTING), any(Instant.class), any(Limit.class)))
                .willReturn(List.of(claimWithId(3L)));

        scheduler.progressClaims();

        verify(claimService).advanceToInTransit(1L);
        verify(claimService).advanceToInspecting(2L);
        verify(claimService).completeRefund(3L);
        verifyNoMoreInteractions(claimService);
    }

    @Test
    @DisplayName("대상이 없으면 ClaimService 를 호출하지 않는다")
    void noCandidates_noop() {
        given(claimRepository.findByStatusAndApprovedAtBefore(any(), any(Instant.class), any(Limit.class)))
                .willReturn(List.of());
        given(claimRepository.findByStatusAndInTransitAtBefore(any(), any(Instant.class), any(Limit.class)))
                .willReturn(List.of());
        given(claimRepository.findByStatusAndInspectingAtBefore(any(), any(Instant.class), any(Limit.class)))
                .willReturn(List.of());

        scheduler.progressClaims();

        verifyNoMoreInteractions(claimService);
    }

    @Test
    @DisplayName("한 건 실패해도 배치의 나머지는 계속 진행한다")
    void perItemFailureIsIsolated() {
        given(claimRepository.findByStatusAndApprovedAtBefore(eq(ClaimStatus.APPROVED), any(Instant.class), any(Limit.class)))
                .willReturn(List.of(claimWithId(1L), claimWithId(2L)));
        given(claimRepository.findByStatusAndInTransitAtBefore(any(), any(Instant.class), any(Limit.class)))
                .willReturn(List.of());
        given(claimRepository.findByStatusAndInspectingAtBefore(any(), any(Instant.class), any(Limit.class)))
                .willReturn(List.of());
        willThrow(new RuntimeException("DB hiccup")).given(claimService).advanceToInTransit(1L);

        scheduler.progressClaims();

        verify(claimService).advanceToInTransit(1L);
        verify(claimService).advanceToInTransit(2L);
    }

    @Test
    @DisplayName("batch-size 가 0 이하면 생성 시점에 거부")
    void rejectsNonPositiveBatchSize() {
        assertThatThrownBy(() -> new ClaimProgressScheduler(claimRepository, claimService, CLOCK, DELAY, DELAY, DELAY, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
