package com.groove.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

/**
 * 동시성/부하 통합 테스트 공용 하니스 (#205 추출). readyGate 로 N 개 요청을 동시 출발시키고
 * wall-clock(elapsedMs)·요청별 지연을 수집해 TPS·p95 를 파생한다.
 *
 * <p>{@code OversellingBaselineTest}(#205)·{@code CouponIssuanceConcurrencyTest}(#93) 가 공유한다 —
 * 측정 방법론을 한 곳에 두어 두 벤치마크 표의 일관성을 보장한다(한쪽만 고쳐 드리프트하는 것을 방지).
 */
public final class ConcurrencyHarness {

    private static final long DONE_GATE_TIMEOUT_SECONDS = 60L;

    private ConcurrencyHarness() {
    }

    /**
     * {@code requests} 개 작업을 {@code threadPoolSize} 스레드 풀에서 readyGate 로 동시 출발시켜 실행한다.
     * 각 작업은 <b>자신의 예외를 직접 처리</b>해야 한다 — 본 하니스는 정상 반환한 요청의 지연만 집계한다.
     *
     * @throws IllegalStateException timeout 안에 모든 요청이 완료되지 않으면(결과 무효)
     */
    public static LoadResult runConcurrently(int threadPoolSize, int requests, IntConsumer task)
            throws InterruptedException {
        CountDownLatch readyGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(requests);
        ConcurrentLinkedQueue<Long> latenciesNanos = new ConcurrentLinkedQueue<>();
        ExecutorService pool = Executors.newFixedThreadPool(threadPoolSize);
        boolean settled;
        long startNanos;
        long endNanos;
        try {
            for (int i = 0; i < requests; i++) {
                final int index = i;
                pool.submit(() -> {
                    try {
                        readyGate.await();
                        long reqStart = System.nanoTime();
                        task.accept(index);
                        latenciesNanos.add(System.nanoTime() - reqStart);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneGate.countDown();
                    }
                });
            }
            startNanos = System.nanoTime();
            readyGate.countDown();
            settled = doneGate.await(DONE_GATE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            endNanos = System.nanoTime();
        } finally {
            // doneGate.await() 가 InterruptedException 을 던지거나 submit 단계에서 예외가 나도
            // ExecutorService 가 반드시 종료되도록 finally 로 감싼다.
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
        if (!settled) {
            throw new IllegalStateException(
                    "동시 요청들이 " + DONE_GATE_TIMEOUT_SECONDS + "s 안에 완료되지 않았다 (결과 무효)");
        }
        long elapsedMs = (endNanos - startNanos) / 1_000_000L;
        return new LoadResult(requests, elapsedMs, new ArrayList<>(latenciesNanos));
    }

    /** 부하 측정 결과 — 처리량(TPS)·p95 지연(ms)을 요청별 지연에서 파생한다. */
    public record LoadResult(int requests, long elapsedMs, List<Long> latenciesNanos) {

        public double tps() {
            return elapsedMs == 0 ? 0.0 : requests / (elapsedMs / 1000.0);
        }

        public long p95Millis() {
            if (latenciesNanos.isEmpty()) {
                return 0L;
            }
            List<Long> sorted = new ArrayList<>(latenciesNanos);
            Collections.sort(sorted);
            int idx = (int) Math.ceil(0.95 * sorted.size()) - 1;
            idx = Math.max(0, Math.min(idx, sorted.size() - 1));
            return sorted.get(idx) / 1_000_000L;
        }
    }
}
