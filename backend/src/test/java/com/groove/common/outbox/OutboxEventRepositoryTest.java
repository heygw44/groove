package com.groove.common.outbox;

import com.groove.common.persistence.JpaAuditingConfig;
import com.groove.support.TestcontainersConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfig.class, JpaAuditingConfig.class})
@org.springframework.test.context.ActiveProfiles("test")
@DisplayName("OutboxEventRepository 통합 테스트 (Testcontainers MySQL)")
class OutboxEventRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-06-16T00:00:00Z");

    @Autowired
    private OutboxEventRepository repository;
    @Autowired
    private EntityManager em;

    private OutboxEvent unpublished() {
        return repository.saveAndFlush(OutboxEvent.of("ORDER", 1L, "ORDER_PAID", "{\"orderId\":1}"));
    }

    private OutboxEvent publishedAt(Instant at) {
        OutboxEvent event = OutboxEvent.of("ORDER", 2L, "ORDER_PAID", "{\"orderId\":2}");
        event.markPublished(at);
        return repository.saveAndFlush(event);
    }

    private OutboxEvent unpublishedWithAttempts(int attemptCount) {
        OutboxEvent event = OutboxEvent.of("ORDER", 3L, "ORDER_PAID", "{\"orderId\":3}");
        ReflectionTestUtils.setField(event, "attemptCount", attemptCount);
        return repository.saveAndFlush(event);
    }

    @Test
    @DisplayName("findByPublishedAtIsNullOrderByIdAsc: 미발행 행만 id 오름차순(FIFO)으로, 발행 완료는 제외")
    void findsOnlyUnpublishedInFifoOrder() {
        // 공유 DB 라 내 id 로 단언한다
        OutboxEvent first = unpublished();
        OutboxEvent published = publishedAt(T0); // 발행 완료
        OutboxEvent third = unpublished();

        var ids = repository.findByPublishedAtIsNullOrderByIdAsc(Limit.of(1000)).stream()
                .map(OutboxEvent::getId).toList();

        assertThat(ids).containsSubsequence(first.getId(), third.getId()); // FIFO 상대 순서 보존
        assertThat(ids).doesNotContain(published.getId());                 // 발행 완료는 제외
    }

    @Test
    @DisplayName("findByPublishedAtIsNullOrderByIdAsc: Limit 으로 배치 크기를 제한한다")
    void respectsLimit() {
        unpublished();
        unpublished();
        unpublished();

        assertThat(repository.findByPublishedAtIsNullOrderByIdAsc(Limit.of(2))).hasSize(2);
    }

    @Test
    @DisplayName("findByPublishedAtIsNullAndAttemptCountLessThan: attempt_count 가 상한 이상인 DLQ 행을 제외한다")
    void findRelayable_excludesAtOrAboveMaxAttempts() {
        int maxAttempts = 5;
        OutboxEvent fresh = unpublishedWithAttempts(0);             // 릴레이 대상
        OutboxEvent belowCap = unpublishedWithAttempts(maxAttempts - 1); // 릴레이 대상(아직 < N)
        OutboxEvent atCap = unpublishedWithAttempts(maxAttempts);   // 격리(>= N)
        OutboxEvent aboveCap = unpublishedWithAttempts(maxAttempts + 1); // 격리(>= N)

        var ids = repository.findByPublishedAtIsNullAndAttemptCountLessThanOrderByIdAsc(maxAttempts, Limit.of(1000))
                .stream().map(OutboxEvent::getId).toList();

        // 공유 DB 라 내 id 로 단언한다
        assertThat(ids).contains(fresh.getId(), belowCap.getId());
        assertThat(ids).doesNotContain(atCap.getId(), aboveCap.getId());
    }

    @Test
    @DisplayName("incrementAttemptCount: 재시도 카운터를 1 증가시킨다")
    void incrementAttemptCount_increments() {
        OutboxEvent event = unpublishedWithAttempts(0);

        assertThat(repository.incrementAttemptCount(event.getId())).isEqualTo(1);

        em.clear();
        assertThat(repository.findById(event.getId()).orElseThrow().getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("markPublished: 미발행 행은 1회만 표시(조건부) — 재호출은 0행")
    void markPublished_isConditional() {
        OutboxEvent event = unpublished();

        assertThat(repository.markPublished(event.getId(), T0)).isEqualTo(1);
        // 이미 발행됨 — 두 번째는 0행
        assertThat(repository.markPublished(event.getId(), T0.plusSeconds(60))).isZero();

        em.clear();
        assertThat(repository.findById(event.getId()).orElseThrow().getPublishedAt()).isEqualTo(T0);
    }

    @Test
    @DisplayName("deletePublishedBefore: 보관 기간이 지난 발행 완료 행만 삭제(미발행·최근 발행은 보존)")
    void deletePublishedBefore_removesOldPublishedOnly() {
        OutboxEvent old = publishedAt(T0.minusSeconds(3600));      // 정리 대상
        OutboxEvent recent = publishedAt(T0.minusSeconds(10));     // 보존(cutoff 이후)
        OutboxEvent pending = unpublished();                       // 보존(미발행)

        int deleted = repository.deletePublishedBefore(T0.minusSeconds(60), 100);

        // 공유 DB 라 1건 이상 + id 로 단언한다
        assertThat(deleted).isGreaterThanOrEqualTo(1);
        em.clear();
        assertThat(repository.findById(old.getId())).isEmpty();
        assertThat(repository.findById(recent.getId())).isPresent();
        assertThat(repository.findById(pending.getId())).isPresent();
    }
}
