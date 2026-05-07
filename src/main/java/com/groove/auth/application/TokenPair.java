package com.groove.auth.application;

/**
 * 로그인·갱신 응답에 실리는 access·refresh 토큰 묶음.
 *
 * <p>{@code accessTokenExpiresInSeconds} 는 클라이언트가 만료 직전 갱신을
 * 트리거하기 위해 사용한다 (절대 epoch 가 아닌 상대 TTL).
 *
 * <p>record 의 자동 {@code toString()} 은 토큰 평문을 노출하므로 마스킹 버전으로
 * 오버라이드한다. 실수로 {@code log.info("tokens={}", tokens)} 같은 코드가
 * 들어가도 토큰이 로그에 남지 않도록 차단한다.
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
