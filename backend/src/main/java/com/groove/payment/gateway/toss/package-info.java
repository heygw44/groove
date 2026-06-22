/**
 * 토스페이먼츠 어댑터(#293).
 *
 * <p>TossPaymentConfig 가 Basic Auth 인터셉터를 단 RestClient 빈을 dev/prod 프로파일에 구성한다.
 * 실 PG 어댑터(TossPaymentGateway)와 웹훅 검증은 다음 이슈에서 이 빈을 주입해 추가한다.
 */
package com.groove.payment.gateway.toss;
