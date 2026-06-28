package com.groove.common.util;

/**
 * 개인식별정보(PII) 마스킹 헬퍼. 공개 응답에서 실명·주소를 부분 가린다.
 * 저장값을 바꾸지 않고 표시 시점에만 마스킹한다(엔티티 익명화 anonymizePii 와 별개).
 */
public final class PiiMasking {

    private static final char MASK = '*';

    private PiiMasking() {
    }

    /**
     * 수령인 이름을 마스킹한다. 첫·끝 글자만 남기고 가운데를 가린다.
     * null/blank → 원본, 1자 → *, 2자 → 홍*, 3자 이상 → 첫·끝 보존 가운데 마스킹(홍길동 → 홍*동, 남궁민수 → 남**수).
     */
    public static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }
        // length()/charAt() 은 UTF-16 code unit 기준이라 보조 평면 문자(이모지·희귀 한자)를 surrogate half 로
        // 쪼갤 수 있으므로 code point 단위로 처리한다.
        int codePoints = name.codePointCount(0, name.length());
        if (codePoints == 1) {
            return String.valueOf(MASK);
        }
        int firstEnd = name.offsetByCodePoints(0, 1);
        if (codePoints == 2) {
            return name.substring(0, firstEnd) + MASK;
        }
        int lastStart = name.offsetByCodePoints(0, codePoints - 1);
        return name.substring(0, firstEnd)
                + String.valueOf(MASK).repeat(codePoints - 2)
                + name.substring(lastStart);
    }

    /**
     * 기본 주소를 부분 마스킹한다. 공백 기준 앞 2토큰(시/도·시/군/구)까지만 남기고 이후(도로명·번지)를 *** 로 가린다.
     * 공개 엔드포인트 노출이라 토큰 수와 무관하게 항상 일부를 가린다(원본 그대로 반환하지 않는다).
     * 3토큰 이상 → 앞 2토큰 + *** , 2토큰 → 첫 토큰 + *** , 1토큰 → *** (지역 구분이 없어 전부 가린다).
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
