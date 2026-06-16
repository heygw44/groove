package com.groove.common.api;

import com.groove.common.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 본 기능의 가장 중요한 정합성 지점인 <b>커서 타입 충실도</b> 가드 (#235). 디코딩된 값이 인코딩 전과
 * <b>동일 런타임 클래스·순서</b> 임을 직접 단언한다 — String 으로 뭉개진 Instant 나 Integer 로 좁혀진 Long
 * 은 keyset 술어를 조용히 깨뜨리기 때문이다.
 */
@DisplayName("CursorCodec — 타입 충실도 round-trip + 위조 커서 방어")
class CursorCodecTest {

    private final CursorCodec codec = new CursorCodec(new ObjectMapper());

    @Test
    @DisplayName("Instant + Long round-trip → 같은 타입·값·순서로 복원")
    void roundTrip_instantAndLong() {
        Instant now = Instant.parse("2026-06-15T09:30:00.123456Z");
        Map<String, Object> keys = new LinkedHashMap<>();
        keys.put("createdAt", now);
        keys.put("id", 42L);

        ScrollPosition decoded = codec.decode(codec.encode(ScrollPosition.forward(keys)));

        Map<String, Object> restored = ((KeysetScrollPosition) decoded).getKeys();
        assertThat(restored.get("createdAt")).isInstanceOf(Instant.class).isEqualTo(now);
        assertThat(restored.get("id")).isInstanceOf(Long.class).isEqualTo(42L);
        assertThat(restored.keySet()).containsExactly("createdAt", "id"); // 순서 보존
    }

    @Test
    @DisplayName("Long + Short(price/releaseYear) round-trip → 좁은 정수 타입도 정확히 복원")
    void roundTrip_longAndShort() {
        Map<String, Object> keys = new LinkedHashMap<>();
        keys.put("price", 39000L);
        keys.put("releaseYear", (short) 2013);
        keys.put("id", 7L);

        Map<String, Object> restored =
                ((KeysetScrollPosition) codec.decode(codec.encode(ScrollPosition.forward(keys)))).getKeys();

        assertThat(restored.get("price")).isInstanceOf(Long.class).isEqualTo(39000L);
        assertThat(restored.get("releaseYear")).isInstanceOf(Short.class).isEqualTo((short) 2013);
        assertThat(restored.get("id")).isInstanceOf(Long.class).isEqualTo(7L);
    }

    @Test
    @DisplayName("디코딩 결과는 forward 방향 keyset 위치다")
    void decodeProducesForwardKeyset() {
        Map<String, Object> keys = new LinkedHashMap<>();
        keys.put("id", 1L);

        ScrollPosition decoded = codec.decode(codec.encode(ScrollPosition.forward(keys)));

        assertThat(decoded).isInstanceOf(KeysetScrollPosition.class);
        assertThat(((KeysetScrollPosition) decoded).scrollsForward()).isTrue();
    }

    @Test
    @DisplayName("Base64 가 아닌 문자열 → ValidationException")
    void invalidBase64_throwsValidation() {
        assertThatThrownBy(() -> codec.decode("not a cursor!!!"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("유효 Base64지만 JSON 이 아님 → ValidationException")
    void validBase64NonJson_throwsValidation() {
        String garbage = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("not-json".getBytes());

        assertThatThrownBy(() -> codec.decode(garbage))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("알 수 없는 버전 → ValidationException")
    void unknownVersion_throwsValidation() {
        String forged = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"v\":99,\"k\":[{\"n\":\"id\",\"t\":\"long\",\"val\":\"1\"}]}".getBytes());

        assertThatThrownBy(() -> codec.decode(forged))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("알 수 없는 타입 태그 → ValidationException")
    void unknownTypeTag_throwsValidation() {
        String forged = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"v\":1,\"k\":[{\"n\":\"id\",\"t\":\"uuid\",\"val\":\"x\"}]}".getBytes());

        assertThatThrownBy(() -> codec.decode(forged))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("resolve: 빈/공백 커서 → 첫 페이지 keyset 위치")
    void resolve_blankCursor_returnsInitial() {
        Sort sort = Sort.by("createdAt", "id");
        assertThat(codec.resolve(null, sort)).isEqualTo(ScrollPosition.keyset());
        assertThat(codec.resolve("   ", sort)).isEqualTo(ScrollPosition.keyset());
    }

    @Test
    @DisplayName("resolve: 커서 키가 활성 정렬 키와 일치 → 디코딩된 위치 반환")
    void resolve_matchingSort_returnsPosition() {
        String cursor = codec.encode(ScrollPosition.forward(keysOf("createdAt", "id")));

        ScrollPosition position = codec.resolve(cursor, Sort.by("createdAt", "id"));

        assertThat(((KeysetScrollPosition) position).getKeys()).containsOnlyKeys("createdAt", "id");
    }

    @Test
    @DisplayName("resolve: 커서 키가 활성 정렬과 불일치 → ValidationException(500 누수 차단)")
    void resolve_mismatchedSort_throws() {
        String cursor = codec.encode(ScrollPosition.forward(keysOf("createdAt", "id")));

        assertThatThrownBy(() -> codec.resolve(cursor, Sort.by("price", "id")))
                .isInstanceOf(ValidationException.class);
    }

    private static java.util.Map<String, Object> keysOf(String dateKey, String idKey) {
        java.util.Map<String, Object> keys = new LinkedHashMap<>();
        keys.put(dateKey, Instant.parse("2026-06-15T00:00:00Z"));
        keys.put(idKey, 9L);
        return keys;
    }
}
