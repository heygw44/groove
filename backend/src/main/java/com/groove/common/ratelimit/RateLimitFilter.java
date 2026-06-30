package com.groove.common.ratelimit;

import com.groove.common.exception.ErrorCode;
import com.groove.common.exception.ProblemDetailEnricher;
import io.github.bucket4j.ConsumptionProbe;
import io.lettuce.core.RedisException;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    static final String HEADER_REMAINING = "X-Rate-Limit-Remaining";
    static final String HEADER_RETRY_AFTER_SECONDS = "X-Rate-Limit-Retry-After-Seconds";
    private static final long NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);
    /** 분산 저장소 장애로 probe 가 없는 fail-closed 차단 시 응답에 실을 Retry-After(초). */
    private static final long STORE_FAILURE_RETRY_SECONDS = 1L;
    /** 저장소 장애 스택트레이스 로그 최소 간격 — 장애 동안 요청마다 찍어 로그 폭탄이 되는 것을 막는다. */
    private static final long STORE_FAILURE_LOG_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(10);

    private final RateLimitRegistry registry;
    private final ObjectMapper objectMapper;
    private final AtomicLong lastStoreFailureLogNanos = new AtomicLong(Long.MIN_VALUE);

    public RateLimitFilter(RateLimitRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return registry.isEmpty();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Optional<RateLimitRegistry.MatchedBucket> matched = registry.match(request);
        if (matched.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitRegistry.MatchedBucket entry = matched.get();
        ConsumptionProbe probe;
        try {
            probe = entry.bucket().tryConsumeAndReturnRemaining(1);
        } catch (RuntimeException ex) {
            if (!isStoreFailure(ex)) {
                throw ex; // 정책/코덱/프로그래밍 오류는 store 장애로 둔갑시키지 않고 표면화한다(500).
            }
            handleStoreFailure(entry.policy(), request, response, filterChain, ex);
            return;
        }

        if (probe.isConsumed()) {
            response.setHeader(HEADER_REMAINING, String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = ceilNanosToSeconds(probe.getNanosToWaitForRefill());
        writeTooManyRequests(response, retryAfterSeconds);
    }

    /**
     * 분산 저장소(Redis) 원격 장애로 토큰 소비가 실패했을 때 정책별로 분기한다. fail-open 정책(로그인 등 가용성
     * 우선)은 한도 미적용으로 통과시키고, fail-closed 정책(쿠폰 발급 — 사재기 억제 우선)은 429 로 차단한다.
     */
    private void handleStoreFailure(RateLimitPolicy policy, HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain, RuntimeException ex) throws ServletException, IOException {
        if (policy.failOpen()) {
            logStoreFailure("fail-open 통과", policy, ex);
            filterChain.doFilter(request, response);
            return;
        }
        logStoreFailure("fail-closed 차단", policy, ex);
        writeTooManyRequests(response, STORE_FAILURE_RETRY_SECONDS);
    }

    /**
     * 저장소 장애 로그를 주기 제한한다. 장애 구간엔 rate-limit 대상 요청마다 이 경로를 타므로, 스택트레이스를
     * 매번 찍으면 로그 I/O 가 fail-open/closed 처리보다 먼저 병목이 된다. 스택 포함 warn 은 간격당 1회만,
     * 나머지는 debug 로 낮춘다.
     */
    private void logStoreFailure(String mode, RateLimitPolicy policy, RuntimeException ex) {
        long now = System.nanoTime();
        long last = lastStoreFailureLogNanos.get();
        if (now - last >= STORE_FAILURE_LOG_INTERVAL_NANOS && lastStoreFailureLogNanos.compareAndSet(last, now)) {
            log.warn("rate-limit 저장소 장애 — {} policy={}", mode, policy.name(), ex);
        } else {
            log.debug("rate-limit 저장소 장애 — {} policy={}", mode, policy.name());
        }
    }

    /** Redis(Lettuce) 원격 장애만 store 장애로 인정한다 — 그 외 RuntimeException(설정·코덱·프로그래밍 오류)은 표면화. */
    private static boolean isStoreFailure(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof RedisException) {
                return true;
            }
        }
        return false;
    }

    private static long ceilNanosToSeconds(long nanos) {
        if (nanos <= 0) {
            return 1L;
        }
        return (nanos + NANOS_PER_SECOND - 1) / NANOS_PER_SECOND;
    }

    private void writeTooManyRequests(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        ErrorCode errorCode = ErrorCode.RATE_LIMIT_EXCEEDED;
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(errorCode.getStatus(), errorCode.getDefaultMessage());
        problem.setTitle(errorCode.getStatus().getReasonPhrase());
        problem.setProperty("code", errorCode.getCode());
        problem.setProperty("retryAfterSeconds", retryAfterSeconds);
        ProblemDetailEnricher.enrich(problem, errorCode.getStatus().value());

        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setHeader(HEADER_RETRY_AFTER_SECONDS, String.valueOf(retryAfterSeconds));
        response.setHeader(HEADER_REMAINING, "0");

        objectMapper.writeValue(response.getWriter(), problem);
    }
}
