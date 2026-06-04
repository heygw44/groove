package com.groove.common.ratelimit;

import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;

import java.util.function.Supplier;

public interface RateLimitPolicy {

    String name();

    boolean appliesTo(HttpServletRequest request);

    Supplier<Bucket> bucketFactory();

    RateLimitKeyResolver keyResolver();
}
