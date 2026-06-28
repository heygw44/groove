package com.groove.payment.api.ratelimit;

import com.groove.common.ratelimit.RateLimitKeyResolver;
import com.groove.common.ratelimit.RateLimitPolicy;
import com.groove.common.ratelimit.RequestPaths;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * POST /api/v1/payments/toss/webhook IP 단위 Rate Limit 정책.
 * 토스 웹훅은 회원 토큰이 없어 회원 키잉 정책에 넣으면 토스 IP 로 몰린 정상 웹훅이 회원 한도(5/분)에 throttle 된다.
 * 그래서 분리하고 IP 키잉한다(한도는 {@link PaymentRateLimitProperties#webhook()}).
 * 위조 paymentKey 는 TossWebhookService 선조회가 outbound 없이 무시하므로, 이 율제는 그 선조회(인덱스 SELECT) 폭주를 캡하는 방어심층이다.
 * 웹훅 유실의 실질 백스톱은 폴링 리퍼. 목 웹훅은 HMAC 서명이 있고 비-prod 전용이라 제외한다.
 */
@Component
public class PaymentWebhookRateLimitPolicy implements RateLimitPolicy {

    static final String NAME = "payment-webhook";
    static final String PATH = "/api/v1/payments/toss/webhook";

    private final PaymentRateLimitProperties.Policy config;

    public PaymentWebhookRateLimitPolicy(PaymentRateLimitProperties properties) {
        this.config = properties.webhook();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean appliesTo(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod())
                && PATH.equals(RequestPaths.normalizedPath(request));
    }

    @Override
    public Supplier<Bucket> bucketFactory() {
        long capacity = config.capacity();
        return () -> Bucket.builder()
                .addLimit(limit -> limit.capacity(capacity).refillGreedy(capacity, config.refillPeriod()))
                .build();
    }

    @Override
    public RateLimitKeyResolver keyResolver() {
        return RateLimitKeyResolver.clientIp();
    }
}
