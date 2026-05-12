package com.groove.order.event;

import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRepository;
import com.groove.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link OrderPaidEventListener} 의 AFTER_COMMIT 트랜잭션 이벤트 보장 (#W7-5).
 *
 * <p>실제 리스너는 로그만 남기는 골격이므로, 검증은 같은 이벤트에 함께 붙인 테스트용 {@link RecordingListener} 로
 * 한다 — Spring 은 한 이벤트에 다중 {@code @TransactionalEventListener} 를 정상 지원한다. Testcontainers MySQL 위에서
 * 실제 트랜잭션을 커밋/롤백하며 다음을 확인한다:
 * <ul>
 *   <li>발행 트랜잭션이 <em>커밋된 뒤에만</em> 리스너가 호출된다 (진행 중에는 미호출).</li>
 *   <li>발행 트랜잭션이 롤백되면 리스너가 호출되지 않는다.</li>
 *   <li>리스너 예외가 이미 커밋된 발행 트랜잭션을 되돌리지 않는다 (격리).</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, OrderPaidEventListenerTest.RecordingConfig.class})
@DisplayName("OrderPaidEventListener — AFTER_COMMIT 트랜잭션 이벤트 (#W7-5)")
class OrderPaidEventListenerTest {

    private static final OrderPaidEvent SAMPLE_EVENT = new OrderPaidEvent(1L, "ORD-TEST-0001", 7L, 42L);

    @Autowired
    private ApplicationEventPublisher publisher;
    @Autowired
    private PlatformTransactionManager txManager;
    @Autowired
    private RecordingListener recorder;
    @Autowired
    private MemberRepository memberRepository;

    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        recorder.reset();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("발행 트랜잭션이 커밋된 뒤에 호출된다 — 트랜잭션 진행 중에는 호출되지 않는다")
    void invokedOnlyAfterCommit() {
        tx.executeWithoutResult(status -> {
            publisher.publishEvent(SAMPLE_EVENT);
            assertThat(recorder.received()).isEmpty(); // 아직 커밋 전 — AFTER_COMMIT 이므로 미호출
        });

        assertThat(recorder.received()).containsExactly(SAMPLE_EVENT);
    }

    @Test
    @DisplayName("발행 트랜잭션이 롤백되면 호출되지 않는다")
    void notInvokedOnRollback() {
        tx.executeWithoutResult(status -> {
            publisher.publishEvent(SAMPLE_EVENT);
            status.setRollbackOnly();
        });

        assertThat(recorder.received()).isEmpty();
    }

    @Test
    @DisplayName("리스너 예외는 이미 커밋된 발행 트랜잭션을 되돌리지 않는다")
    void listenerExceptionDoesNotRollBackPublisher() {
        recorder.failOnNext();
        String email = "after-commit-" + UUID.randomUUID() + "@groove.test";

        // AFTER_COMMIT 리스너는 커밋 이후에 실행되며, 던진 예외는 트랜잭션 동기화 수준에서 흡수된다 —
        // 발행 측 트랜잭션은 이미 커밋되었으므로 되돌릴 것도, 호출자에게 새어 나갈 것도 없다.
        tx.executeWithoutResult(status -> {
            memberRepository.save(Member.register(email, "hash", "테스터", "010-0000-0000"));
            publisher.publishEvent(SAMPLE_EVENT);
        });

        // 리스너가 실제로 실행됐고(그 안에서 터졌고), 그럼에도 발행 트랜잭션의 쓰기는 커밋되어 있다.
        assertThat(recorder.received()).containsExactly(SAMPLE_EVENT);
        assertThat(memberRepository.existsByEmail(email)).isTrue();
    }

    @TestConfiguration
    static class RecordingConfig {
        @Bean
        RecordingListener orderPaidEventRecorder() {
            return new RecordingListener();
        }
    }

    /** {@link OrderPaidEvent} 를 AFTER_COMMIT 으로 함께 수신하는 테스트 더블 — 호출 기록 + 선택적 예외 유발. */
    static class RecordingListener {

        private final List<OrderPaidEvent> received = new CopyOnWriteArrayList<>();
        private final AtomicBoolean failNext = new AtomicBoolean(false);

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void onOrderPaid(OrderPaidEvent event) {
            received.add(event);
            if (failNext.compareAndSet(true, false)) {
                throw new IllegalStateException("의도된 리스너 예외 — 발행 트랜잭션 격리 검증용");
            }
        }

        List<OrderPaidEvent> received() {
            return List.copyOf(received);
        }

        void failOnNext() {
            failNext.set(true);
        }

        void reset() {
            received.clear();
            failNext.set(false);
        }
    }
}
