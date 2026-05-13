package com.groove.order.application;

import com.groove.order.domain.OrderNumberFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class RandomOrderNumberGeneratorTest {

    private static final Pattern FORMAT = Pattern.compile(OrderNumberFormat.PATTERN);

    @Test
    @DisplayName("형식이 ORD-YYYYMMDD-XXXXXX (XXXXXX = [A-Z0-9]) 와 일치한다")
    void generate_matchesFormat() {
        Clock fixed = Clock.fixed(Instant.parse("2026-05-08T12:34:56Z"), ZoneId.of("UTC"));
        RandomOrderNumberGenerator generator = new RandomOrderNumberGenerator(fixed);

        for (int i = 0; i < 100; i++) {
            String number = generator.generate();
            assertThat(number).matches(FORMAT);
        }
    }

    @Test
    @DisplayName("KST 기준 일자가 반영된다 (UTC 23:00 → KST 다음 날)")
    void generate_usesKstDate() {
        Clock utcLateNight = Clock.fixed(Instant.parse("2026-05-08T15:30:00Z"), ZoneId.of("UTC"));
        RandomOrderNumberGenerator generator = new RandomOrderNumberGenerator(utcLateNight);

        String number = generator.generate();

        assertThat(number).startsWith("ORD-20260509-");
    }

    @Test
    @DisplayName("100회 호출 시 모두 서로 다른 값 (확률적, 36^6 공간에서 충돌 매우 드묾)")
    void generate_returnsUniqueValues() {
        Clock fixed = Clock.fixed(Instant.parse("2026-05-08T12:00:00Z"), ZoneId.of("UTC"));
        RandomOrderNumberGenerator generator = new RandomOrderNumberGenerator(fixed);

        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            seen.add(generator.generate());
        }

        assertThat(seen).hasSize(100);
    }
}
