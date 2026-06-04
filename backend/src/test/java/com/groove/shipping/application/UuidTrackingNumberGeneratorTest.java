package com.groove.shipping.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

@DisplayName("UuidTrackingNumberGenerator")
class UuidTrackingNumberGeneratorTest {

    private final UuidTrackingNumberGenerator generator = new UuidTrackingNumberGenerator();

    @Test
    @DisplayName("발급 값은 50자 이하의 UUID 문자열")
    void generatesUuidWithinColumnLength() {
        String tracking = generator.generate();

        assertThat(tracking).hasSizeLessThanOrEqualTo(50);
        assertThatNoException().isThrownBy(() -> UUID.fromString(tracking));
    }

    @Test
    @DisplayName("연속 발급은 서로 다른 값 (충돌 없음)")
    void generatesDistinctValues() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            assertThat(seen.add(generator.generate())).isTrue();
        }
    }
}
