package com.groove.common.api;

import com.groove.common.exception.ErrorCode;
import com.groove.common.exception.ValidationException;
import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * keyset 커서 ↔ KeysetScrollPosition 코덱. 커서는 정렬 키-값 맵과 정렬 시그니처를 불투명 문자열로
 * 직렬화한 것으로, 각 값을 타입 태그와 함께 저장하고 디코딩 시 원래 런타임 타입으로 복원한다.
 *
 * 형식: Base64URL( JSON{ v=포맷버전, s=정렬 시그니처(속성:방향, 순서 보존), k=키 목록(n=속성명·t=타입태그·val=문자열값) } ).
 * 잘못되거나 위조된 커서는 모두 ValidationException(400 VALID_001) 으로 거절한다.
 */
@Component
public class CursorCodec {

    private static final int FORMAT_VERSION = 1;

    private final ObjectMapper objectMapper;

    public CursorCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 요청 커서를 현재 정렬에 맞는 keyset 위치로 해석한다. 비어 있으면 첫 페이지(ScrollPosition.keyset()),
     * 있으면 디코딩하되 커서의 정렬 시그니처(속성·방향·순서)가 활성 정렬과 일치하는지 검증한다. 불일치는
     * 400 VALID_001.
     */
    public ScrollPosition resolve(String cursor, Sort sort) {
        if (cursor == null || cursor.isBlank()) {
            return ScrollPosition.keyset();
        }
        Payload payload = parse(cursor);
        if (!signature(sort).equals(payload.s())) {
            throw new ValidationException(ErrorCode.VALIDATION_FAILED, "커서가 현재 정렬과 일치하지 않습니다");
        }
        return toPosition(payload);
    }

    /**
     * keyset 위치를 현재 정렬 시그니처와 함께 불투명 커서로 인코딩한다. 지원하지 않는 키 값 타입은
     * IllegalArgumentException 을 던진다.
     */
    public String encode(ScrollPosition position, Sort sort) {
        if (!(position instanceof KeysetScrollPosition keyset)) {
            throw new IllegalArgumentException("keyset 위치가 아닙니다: " + position);
        }
        List<Key> keys = new ArrayList<>();
        for (Map.Entry<String, Object> entry : keyset.getKeys().entrySet()) {
            keys.add(toKey(entry.getKey(), entry.getValue()));
        }
        String json = objectMapper.writeValueAsString(new Payload(FORMAT_VERSION, signature(sort), keys));
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 불투명 커서를 forward 방향 keyset 위치로 디코딩한다(정렬 시그니처 검증 없이 위치만 복원).
     * Base64/JSON/타입 파싱 실패는 모두 400 VALID_001 로 변환한다.
     */
    public ScrollPosition decode(String cursor) {
        return toPosition(parse(cursor));
    }

    /** 정렬을 "속성:방향" 시그니처 목록으로 직렬화한다. */
    private List<String> signature(Sort sort) {
        List<String> sig = new ArrayList<>();
        sort.forEach(order -> sig.add(order.getProperty() + ":" + order.getDirection().name()));
        return sig;
    }

    /** Base64URL → JSON → Payload(버전·필수 필드 검증). 실패는 400 VALID_001. */
    private Payload parse(String cursor) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            Payload payload = objectMapper.readValue(decoded, Payload.class);
            if (payload.v() != FORMAT_VERSION || payload.s() == null || payload.k() == null) {
                throw new IllegalArgumentException("지원하지 않는 커서 버전");
            }
            return payload;
        } catch (ValidationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ValidationException(ErrorCode.VALIDATION_FAILED, "잘못된 커서입니다");
        }
    }

    /** Payload 의 키 목록을 런타임 타입 그대로 복원해 forward keyset 위치를 만든다(순서 보존). */
    private ScrollPosition toPosition(Payload payload) {
        try {
            Map<String, Object> keys = new LinkedHashMap<>();
            for (Key key : payload.k()) {
                keys.put(key.n(), fromKey(key));
            }
            if (keys.isEmpty()) {
                throw new IllegalArgumentException("빈 커서");
            }
            return ScrollPosition.forward(keys);
        } catch (ValidationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ValidationException(ErrorCode.VALIDATION_FAILED, "잘못된 커서입니다");
        }
    }

    private Key toKey(String name, Object value) {
        return switch (value) {
            case Instant instant -> new Key(name, "instant", instant.toString());
            case Long l -> new Key(name, "long", l.toString());
            case Integer i -> new Key(name, "int", i.toString());
            case Short s -> new Key(name, "short", s.toString());
            case Boolean b -> new Key(name, "boolean", b.toString());
            case String s -> new Key(name, "string", s);
            case null -> throw new IllegalArgumentException("커서 키 값이 null 입니다: " + name);
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 커서 키 타입: " + value.getClass().getName());
        };
    }

    private Object fromKey(Key key) {
        if (key.t() == null || key.val() == null) {
            throw new IllegalArgumentException("커서 키가 손상되었습니다");
        }
        return switch (key.t()) {
            case "instant" -> Instant.parse(key.val());
            case "long" -> Long.valueOf(key.val());
            case "int" -> Integer.valueOf(key.val());
            case "short" -> Short.valueOf(key.val());
            case "boolean" -> parseStrictBoolean(key.val());
            case "string" -> key.val();
            default -> throw new IllegalArgumentException("알 수 없는 커서 타입 태그: " + key.t());
        };
    }

    /** 불리언 커서 값을 엄격 파싱한다 — "true"/"false" 외에는 거절한다. */
    private boolean parseStrictBoolean(String value) {
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        throw new IllegalArgumentException("불리언 커서 값이 손상되었습니다: " + value);
    }

    /** 커서 페이로드: 포맷 버전(v) + 정렬 시그니처(s) + 정렬 순서를 보존한 키 목록(k). */
    public record Payload(int v, List<String> s, List<Key> k) {
    }

    /** 단일 정렬 키: 속성명(n), 타입 태그(t), 문자열 값(val). */
    public record Key(String n, String t, String val) {
    }
}
