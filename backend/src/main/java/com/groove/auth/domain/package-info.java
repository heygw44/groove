/**
 * 인증 도메인 영속화 모델.
 *
 * <p>Refresh Token Rotation 의 영속 모델({@link com.groove.auth.domain.RefreshToken})과
 * 영속성 컨트랙트({@link com.groove.auth.domain.RefreshTokenRepository}),
 * 토큰 해싱 유틸({@link com.groove.auth.domain.TokenHasher}) 을 제공한다.
 *
 * <p>이 패키지는 토큰의 <b>해시만</b> 저장한다. 평문 토큰은 절대 영속화되지 않으며
 * 서비스 레이어에서 항상 {@code TokenHasher.sha256Hex} 를 거쳐 조회·저장된다.
 */
package com.groove.auth.domain;
