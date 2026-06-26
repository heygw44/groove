package com.groove.payment.gateway.toss;

import com.groove.payment.domain.PaymentMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Normalizer;

/**
 * 토스 결제수단 문자열(한글) → 도메인 {@link PaymentMethod} 매퍼.
 *
 * TossStatusMapper 와 달리 미지/누락 값을 예외 대신 null 반환(보정 스킵). status 는 결제 진행 필수라 미지값을 거부하지만,
 * method 는 기록·표시용이라 새 결제수단 라벨이 추가됐다고 이미 확정된 결제를 실패시키면 안 된다. 미지값은 경고만 남기고 잠정 method 를 유지한다.
 */
final class TossMethodMapper {

    private static final Logger log = LoggerFactory.getLogger(TossMethodMapper.class);

    private TossMethodMapper() {
    }

    /**
     * 토스 method 문자열을 도메인 수단으로 매핑. null/공백/미지값은 null(보정 스킵).
     * 외부 PG 문자열이라 정확 일치 전에 strip + NFC 정규화한다 — 후행 공백·NFD 분해형 한글이 섞여도 매핑이 깨지지 않게.
     */
    static PaymentMethod toPaymentMethod(String tossMethod) {
        if (tossMethod == null || tossMethod.isBlank()) {
            return null;
        }
        String normalized = Normalizer.normalize(tossMethod.strip(), Normalizer.Form.NFC);
        return switch (normalized) {
            case "카드" -> PaymentMethod.CARD;
            case "계좌이체" -> PaymentMethod.BANK_TRANSFER;
            case "가상계좌" -> PaymentMethod.VIRTUAL_ACCOUNT;
            case "간편결제" -> PaymentMethod.EASY_PAY;
            case "휴대폰" -> PaymentMethod.MOBILE_PHONE;
            case "문화상품권", "도서문화상품권", "게임문화상품권" -> PaymentMethod.GIFT_CERTIFICATE;
            default -> {
                log.warn("알 수 없는 토스 결제수단: {} — method 보정을 건너뜁니다", tossMethod);
                yield null;
            }
        };
    }
}
