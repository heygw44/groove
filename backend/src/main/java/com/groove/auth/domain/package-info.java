/**
 * 인증 도메인 영속화 모델.
 *
 * Refresh Token Rotation 의 영속 모델(RefreshToken)과 영속성 컨트랙트(RefreshTokenRepository),
 * 토큰 해싱 유틸(TokenHasher) 을 제공한다.
 *
 * 이 패키지는 토큰의 해시만 저장한다. 평문 토큰은 절대 영속화되지 않으며 서비스 레이어에서
 * 항상 TokenHasher.sha256Hex 를 거쳐 조회·저장된다.
 */
package com.groove.auth.domain;
