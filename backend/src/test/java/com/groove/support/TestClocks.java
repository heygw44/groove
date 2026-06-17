package com.groove.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * 테스트 공용 고정 Clock. 서비스에 Clock 을 주입하는 단위 테스트가 동일한 기준 시각을 공유해
 * 결정적인 타임스탬프를 만들도록 한다. 기준 시각을 바꾸려면 여기 한 곳만 수정한다.
 */
public final class TestClocks {

    private TestClocks() {
    }

    /** 고정 기준 시각 (UTC). */
    public static final Instant FIXED_INSTANT = Instant.parse("2026-06-17T00:00:00Z");

    /** FIXED_INSTANT 에 고정된 UTC Clock. */
    public static final Clock FIXED = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
}
