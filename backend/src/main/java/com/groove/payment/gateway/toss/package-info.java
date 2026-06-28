/**
 * 토스페이먼츠 어댑터.
 * TossPaymentConfig 가 Basic Auth 인터셉터를 단 RestClient 빈을 dev/prod 프로파일에 구성하고,
 * TossPaymentGateway 가 이 빈을 주입해 confirm/query/cancel 코어 API 를 호출한다.
 * 동기 confirm 모델이라 confirm 이 진입점이며 request() 는 미지원이다.
 */
package com.groove.payment.gateway.toss;
