package com.groove.order.application;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * ORD-YYYYMMDD-XXXXXX — XXXXXX 는 [A-Z0-9]^6. 일자는 KST(Asia/Seoul) 기준으로 표기한다.
 */
@Component
public class RandomOrderNumberGenerator {

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

    public String generate() {
        String date = LocalDate.now(clock.withZone(KST)).format(DATE);
        StringBuilder suffix = new StringBuilder(SUFFIX_LENGTH);
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            suffix.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return "ORD-" + date + "-" + suffix;
    }
}
