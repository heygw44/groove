package com.groove.payment.gateway;

/** 결제 결과 웹훅 통보를 수신 측에 전달하는 포트. */
@FunctionalInterface
public interface WebhookDispatcher {

    void dispatch(WebhookNotification notification);
}
