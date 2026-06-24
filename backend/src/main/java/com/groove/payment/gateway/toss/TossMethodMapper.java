package com.groove.payment.gateway.toss;

import com.groove.payment.domain.PaymentMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Normalizer;

/**
 * 토스 결제수단 문자열(한글) → 도메인 {@link PaymentMethod} 매퍼.
 *
 * <pre>
 *   카드                                          → CARD
 *   계좌이체                                       → BANK_TRANSFER
 *   가상계좌                                       → VIRTUAL_ACCOUNT
 *   간편결제                                       → EASY_PAY
 *   휴대폰                                         → MOBILE_PHONE
 *   문화상품권 / 도서문화상품권 / 게임문화상품권      → GIFT_CERTIFICATE
 * </pre>
 *
 * <p><b>{@link TossStatusMapper} 와 의도적으로 다른 점:</b> 미지/누락 값을 예외로 던지지 않고 {@code null} 을
 * 반환한다(보정 스킵). status 는 결제 진행에 필수라 미지값을 거부해야 하지만, method 는 기록·표시용이라
 * 토스가 새 결제수단 라벨을 추가했다고 <b>이미 확정된 결제를 실패시켜서는 안 된다</b>. 미지값은 경고만 남기고
 * 호출부가 잠정 method 를 유지하도록 한다.
 */
final class TossMethodMapper {

    private static final Logger log = LoggerFactory.getLogger(TossMethodMapper.class);

    private TossMethodMapper() {
    }

    /**
     * 토스 method 문자열을 도메인 수단으로 매핑한다. null/공백/미지값은 {@code null}(보정 스킵).
     *
     * <p>외부 PG 응답 문자열이라 정확 일치 전에 앞뒤 공백을 제거하고 유니코드 NFC 로 정규화한다 — 후행 공백·NFD
     * 분해형 한글이 섞여도 매핑이 깨지지 않도록 한다.
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
