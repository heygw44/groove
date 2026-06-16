package com.groove.payment.gateway;

/** 결제 웹훅 서명 검증 포트. */
@FunctionalInterface
public interface WebhookSignatureVerifier {

    /** 서명을 검증한다. 유효하지 않으면 InvalidWebhookSignatureException(401). */
    void verify(String signature);
}
