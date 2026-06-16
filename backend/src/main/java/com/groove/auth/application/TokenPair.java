package com.groove.auth.application;

/**
 * 로그인·갱신 응답에 실리는 access·refresh 토큰 묶음.
 *
 * <p>accessTokenExpiresInSeconds 는 상대 TTL(초) 이다.
 *
 * <p>toString() 은 토큰 평문을 별표로 마스킹해 오버라이드한다.
 */
public record TokenPair(
        String accessToken,
        String refreshToken,
        long accessTokenExpiresInSeconds
) {

    @Override
    public String toString() {
        return "TokenPair[accessToken=***, refreshToken=***, accessTokenExpiresInSeconds="
                + accessTokenExpiresInSeconds + "]";
    }
}
