package com.groove.auth.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CorsProperties — 와일드카드+자격증명 가드 (이슈 #166)")
class CorsPropertiesTest {

    /** 가드와 무관한 필드(methods/headers/exposed/maxAge)는 null 로 두고 compact constructor 가 defaulting 하게 한다. */
    private static CorsProperties cors(List<String> patterns, List<String> origins, Boolean allowCredentials) {
        return new CorsProperties(patterns, origins, null, null, null, allowCredentials, null);
    }

    @Test
    @DisplayName("allow-credentials=true + allowed-origin-patterns 의 와일드카드는 거부한다")
    void wildcardPattern_withCredentials_throws() {
        assertThatThrownBy(() -> cors(List.of("*"), List.of(), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이슈 #166");
    }

    @Test
    @DisplayName("allow-credentials=true + allowed-origins 의 와일드카드는 거부한다")
    void wildcardOrigin_withCredentials_throws() {
        assertThatThrownBy(() -> cors(List.of(), List.of("*"), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이슈 #166");
    }

    @Test
    @DisplayName("구체적 포트 와일드카드 패턴은 credentials 와 함께 허용한다 (local/test 프로파일)")
    void concretePortPattern_withCredentials_passes() {
        assertThatCode(() -> cors(List.of("http://localhost:[*]"), List.of(), true))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("credentials=false 면 와일드카드 패턴도 허용한다")
    void wildcard_withoutCredentials_passes() {
        assertThatCode(() -> cors(List.of("*"), List.of(), false))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("credentials=null(→false) 이면 와일드카드 패턴도 허용한다")
    void wildcard_withNullCredentials_passes() {
        assertThatCode(() -> cors(List.of("*"), List.of(), null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("origin 목록이 비어 있으면 credentials=true 라도 통과한다 (default application.yaml 자세)")
    void emptyOrigins_withCredentials_passes() {
        assertThatCode(() -> cors(List.of(), List.of(), true))
                .doesNotThrowAnyException();
    }
}
