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
 * keyset 커서 ↔ {@link KeysetScrollPosition} 코덱 (#235).
 *
 * <p>커서는 {@link KeysetScrollPosition#getKeys()}(정렬 키 → 값 맵)와 그 커서를 만든 <b>정렬 시그니처</b>를
 * 클라이언트가 다음 페이지 요청에 그대로 되돌려줄 <b>불투명 문자열</b>로 직렬화한 것이다. 이 코덱의
 * 핵심 정합성 지점은 <b>타입 충실도</b>다 — {@code createdAt}({@link Instant}) 가 {@code String} 으로,
 * {@code id}({@link Long}) 가 {@link Integer} 로 복원되면 keyset 술어가 조용히 깨져 페이지 경계에서
 * 누락/중복 또는 비교 실패(500)가 난다. 따라서 각 값을 닫힌 집합의 <b>타입 태그</b>와 함께 문자열로
 * 저장하고, 디코딩 시 원래 런타임 타입으로 복원한다.
 *
 * <p>직렬화 형식: {@code Base64URL( JSON{ v, s:["prop:DIR", …], k:[{n,t,val}, …] } )}.
 * {@code v}=포맷 버전, {@code s}=정렬 시그니처(속성:방향, 순서 보존), {@code k}=정렬 순서를 보존한 키 목록
 * ({@code n}=속성명, {@code t}=타입태그, {@code val}=문자열값). 순서 보존을 위해 디코딩 결과는
 * {@link LinkedHashMap} 으로 구성한다.
 *
 * <p>{@link #resolve(String, Sort)} 는 커서의 {@code s} 가 활성 정렬과 <b>속성·방향·순서까지 정확히 일치</b>
 * 하는지 검증한다(CodeRabbit 리뷰). 키 이름만 같고 방향/순서가 다른 커서를 재사용하면 keyset 경계 의미가
 * 달라지고 Spring Data 가 술어 생성 시 예외(→500)를 낼 수 있어, 불일치는 400 {@code VALID_001} 로 막는다.
 *
 * <p>서명(HMAC)은 두지 않는다 — 커서는 호출자가 이미 조회 권한을 가진 행의 정렬 튜플만 노출하고,
 * 회원 스코프({@code memberId})는 커서에 담지 않고 항상 서버에서 인증 주체로부터 취하기 때문이다.
 * 잘못되거나 위조된 커서는 모두 {@link ValidationException}(400 {@code VALID_001}) 으로 거절한다.
 */
@Component
public class CursorCodec {

    private static final int FORMAT_VERSION = 1;

    private final ObjectMapper objectMapper;

    public CursorCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 요청 커서를 현재 정렬에 맞는 keyset 위치로 해석한다. 비어 있으면 첫 페이지({@link ScrollPosition#keyset()}),
     * 있으면 디코딩하되 <b>커서에 박힌 정렬 시그니처(속성·방향·순서)가 활성 정렬과 정확히 일치</b>하는지 검증한다.
     *
     * <p>키 집합뿐 아니라 방향·순서까지 일치를 요구한다 — {@code sort=createdAt,desc} 로 만든 커서를
     * {@code sort=price,asc}(다른 속성)나 {@code sort=createdAt,asc}(같은 속성 다른 방향)로 재요청하면
     * keyset 경계 의미가 달라진다. 불일치는 여기서 400 {@code VALID_001} 로 막아, Spring Data 가 keyset 술어
     * 생성 시 {@code IllegalStateException}(→500)을 던지는 것을 사전에 차단한다.
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
     * 프로그래밍 오류이므로 {@link IllegalArgumentException} 으로 즉시 드러낸다(새 정렬 키 도입 시 코덱·테스트가
     * 함께 갱신되도록).
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
     * 불투명 커서를 forward 방향 keyset 위치로 디코딩한다(정렬 시그니처 검증 없이 위치만 복원). 정렬 일치
     * 검증까지 필요한 호출 경로는 {@link #resolve(String, Sort)} 를 쓴다. Base64/JSON/타입 파싱 실패는 모두
     * 400 {@code VALID_001} 로 변환한다(위조·손상 커서 방어).
     */
    public ScrollPosition decode(String cursor) {
        return toPosition(parse(cursor));
    }

    /** 정렬을 {@code "속성:방향"} 시그니처 목록으로 직렬화 — 방향·순서까지 포함해 커서-정렬 일치를 엄격 검증한다. */
    private List<String> signature(Sort sort) {
        List<String> sig = new ArrayList<>();
        sort.forEach(order -> sig.add(order.getProperty() + ":" + order.getDirection().name()));
        return sig;
    }

    /** Base64URL → JSON → {@link Payload}(버전·필수 필드 검증). 실패는 400 {@code VALID_001}. */
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

    /** {@link Payload} 의 키 목록을 런타임 타입 그대로 복원해 forward keyset 위치를 만든다(순서 보존). */
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

    /**
     * 불리언 커서 값을 엄격 파싱한다 — {@link Boolean#valueOf(String)} 는 잘못된 문자열을 조용히
     * {@code false} 로 받아들여 위조 커서가 통과하므로, {@code "true"/"false"} 외에는 거절한다(CodeRabbit 리뷰).
     */
    private boolean parseStrictBoolean(String value) {
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        throw new IllegalArgumentException("불리언 커서 값이 손상되었습니다: " + value);
    }

    /** 커서 페이로드: 포맷 버전 + 정렬 시그니처({@code s}) + 정렬 순서를 보존한 키 목록({@code k}). */
    public record Payload(int v, List<String> s, List<Key> k) {
    }

    /** 단일 정렬 키: 속성명({@code n}), 타입 태그({@code t}), 문자열 값({@code val}). */
    public record Key(String n, String t, String val) {
    }
}
