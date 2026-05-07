package com.groove.auth.application;

/**
 * 로그인·갱신 응답에 실리는 access·refresh 토큰 묶음.
 *
 * <p>{@code accessTokenExpiresInSeconds} 는 클라이언트가 만료 직전 갱신을
 * 트리거하기 위해 사용한다 (절대 epoch 가 아닌 상대 TTL).
 */
public record TokenPair(
        String accessToken,
        String refreshToken,
        long accessTokenExpiresInSeconds
) {
}
