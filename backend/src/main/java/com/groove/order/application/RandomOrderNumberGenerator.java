package com.groove.order.application;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * {@code ORD-YYYYMMDD-XXXXXX} (API.md §3.5) — XXXXXX 는 [A-Z0-9]^6.
 *
 * <p>{@code Clock} 은 {@code SecurityConfig} 가 등록한 system UTC 빈을 주입받지만, 일자 표기는
 * KST(Asia/Seoul) 기준으로 변환한다 — 사용자/운영자 관점에서 자정 경계가 한국 시간이어야 자연스럽다.
 *
 * <p>충돌 확률: 36^6 = 약 21억. 같은 날 1만건 발급 시 충돌 기대값 ≈ 23 — UNIQUE 제약 + 호출 측 재시도로 흡수한다.
 */
@Component
public class RandomOrderNumberGenerator implements OrderNumberGenerator {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int SUFFIX_LENGTH = 6;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final Clock clock;
    private final SecureRandom random;

    public RandomOrderNumberGenerator(Clock clock) {
        this.clock = clock;
        this.random = new SecureRandom();
    }

    @Override
    public String generate() {
        String date = LocalDate.now(clock.withZone(KST)).format(DATE);
        StringBuilder suffix = new StringBuilder(SUFFIX_LENGTH);
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            suffix.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return "ORD-" + date + "-" + suffix;
    }
}
