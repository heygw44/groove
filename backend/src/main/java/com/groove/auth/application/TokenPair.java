package com.groove.auth.application;

/**
 * 로그인·갱신 응답에 실리는 access·refresh 토큰 묶음. accessTokenExpiresInSeconds 는 상대 TTL(초).
 * toString() 은 토큰 평문을 별표로 마스킹한다.
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
