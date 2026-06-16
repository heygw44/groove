package com.groove.auth.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CorsProperties — host 와일드카드+자격증명 가드 (이슈 #166)")
class CorsPropertiesTest {

    /** 가드와 무관한 필드(methods/headers/exposed/maxAge)는 null 로 둔다. */
    private static CorsProperties cors(List<String> patterns, List<String> origins, Boolean allowCredentials) {
        return new CorsProperties(patterns, origins, null, null, null, allowCredentials, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "http://*", "https://*", "*://*", "http://*:8080"})
    @DisplayName("allow-credentials=true + host 전체 와일드카드 패턴은 거부한다")
    void anyHostWildcardPattern_withCredentials_throws(String pattern) {
        assertThatThrownBy(() -> cors(List.of(pattern), List.of(), true))
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

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:[*]", "http://127.0.0.1:[*]", "http://[::1]:[*]",
            "https://*.example.com", "https://app.example.com"})
    @DisplayName("host 가 한정된 패턴(포트·서브도메인 와일드카드 포함)은 credentials 와 함께 허용한다")
    void hostRestrictedPattern_withCredentials_passes(String pattern) {
        assertThatCode(() -> cors(List.of(pattern), List.of(), true))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("credentials=false 면 host 와일드카드 패턴도 허용한다")
    void wildcard_withoutCredentials_passes() {
        assertThatCode(() -> cors(List.of("*"), List.of(), false))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("credentials=null(→false) 이면 host 와일드카드 패턴도 허용한다")
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
