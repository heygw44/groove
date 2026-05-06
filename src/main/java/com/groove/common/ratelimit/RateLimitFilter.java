package com.groove.common.ratelimit;

import com.groove.common.exception.ErrorCode;
import com.groove.common.exception.ProblemDetailEnricher;
import io.github.bucket4j.ConsumptionProbe;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class RateLimitFilter extends OncePerRequestFilter {

    static final String HEADER_REMAINING = "X-Rate-Limit-Remaining";
    static final String HEADER_RETRY_AFTER_SECONDS = "X-Rate-Limit-Retry-After-Seconds";
    private static final long NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);

    private final RateLimitRegistry registry;
    private final ObjectMapper objectMapper;

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
        ConsumptionProbe probe = entry.bucket().tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.setHeader(HEADER_REMAINING, String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = ceilNanosToSeconds(probe.getNanosToWaitForRefill());
        writeTooManyRequests(response, retryAfterSeconds);
    }

    private static long ceilNanosToSeconds(long nanos) {
        if (nanos <= 0) {
            return 1L;
        }
        long seconds = (nanos + NANOS_PER_SECOND - 1) / NANOS_PER_SECOND;
        return Math.max(1L, seconds);
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
