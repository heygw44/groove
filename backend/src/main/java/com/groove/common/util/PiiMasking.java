package com.groove.common.util;

/**
 * 개인식별정보(PII) 마스킹 헬퍼. 공개 응답에서 실명·주소를 부분 가린다(#322).
 *
 * <p>저장값을 바꾸지 않고 표시 시점에만 마스킹한다(엔티티 익명화 {@code anonymizePii} 와 별개).
 */
public final class PiiMasking {

    private static final char MASK = '*';

    private PiiMasking() {
    }

    /**
     * 수령인 이름을 마스킹한다. 첫·끝 글자만 남기고 가운데를 가린다.
     * <ul>
     *   <li>null/blank → 원본 그대로</li>
     *   <li>1자 → {@code *}</li>
     *   <li>2자 → {@code 홍*}</li>
     *   <li>3자 이상 → 첫·끝 보존, 가운데 마스킹 (홍길동 → 홍*동, 남궁민수 → 남**수)</li>
     * </ul>
     */
    public static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }
        int length = name.length();
        if (length == 1) {
            return String.valueOf(MASK);
        }
        if (length == 2) {
            return name.charAt(0) + String.valueOf(MASK);
        }
        return name.charAt(0)
                + String.valueOf(MASK).repeat(length - 2)
                + name.charAt(length - 1);
    }

    /**
     * 기본 주소를 부분 마스킹한다. 공백 기준 앞 2토큰(시/도·시/군/구)까지만 남기고 이후(도로명·번지)를 {@code ***} 로 가린다.
     * 공개 엔드포인트 노출이므로 토큰 수와 무관하게 **항상** 일부를 가린다(원본 그대로 반환하지 않는다).
     * <ul>
     *   <li>3토큰 이상 → 앞 2토큰 + {@code ***} ("서울특별시 강남구 테헤란로 123" → "서울특별시 강남구 ***")</li>
     *   <li>2토큰 → 첫 토큰 + {@code ***} ("제주시 1100로" → "제주시 ***")</li>
     *   <li>1토큰 → {@code ***} (지역 구분이 없어 전부 가린다)</li>
     * </ul>
     */
    public static String maskAddress(String address) {
        if (address == null || address.isBlank()) {
            return address;
        }
        String[] tokens = address.trim().split("\\s+");
        if (tokens.length == 1) {
            return "***";
        }
        if (tokens.length == 2) {
            return tokens[0] + " ***";
        }
        return tokens[0] + " " + tokens[1] + " ***";
    }
}
