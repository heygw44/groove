package com.groove.auth.security;

import com.groove.common.exception.AuthException;
import com.groove.common.exception.ErrorCode;
import com.groove.member.domain.MemberRole;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtProvider")
class JwtProviderTest {

    private static final String FIXTURE_HMAC_BYTES = "fixture-hmac-bytes-must-be-at-least-32-bytes-long-for-hs256!";
    private static final long ACCESS_TTL = 1800L;
    private static final long REFRESH_TTL = 1209600L;
    private static final Instant FIXED_NOW = Instant.parse("2026-05-07T10:00:00Z");

    private JwtProperties properties;
    private Clock fixedClock;
    private JwtProvider provider;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties(FIXTURE_HMAC_BYTES, ACCESS_TTL, REFRESH_TTL);
        fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        provider = new JwtProvider(properties, fixedClock);
    }

    @Nested
    @DisplayName("Access Token 발급/파싱")
    class AccessToken {

        @Test
        @DisplayName("발급한 Access Token 을 파싱하면 memberId 와 role 이 일치한다")
        void issueAndParse_returnsClaims() {
            String token = provider.issueAccessToken(42L, MemberRole.USER);

            JwtClaims claims = provider.parseAccessToken(token);

            assertThat(claims.memberId()).isEqualTo(42L);
            assertThat(claims.role()).isEqualTo(MemberRole.USER);
        }

        @Test
        @DisplayName("ADMIN 권한도 정상적으로 round-trip 된다")
        void issueAndParse_admin_role() {
            String token = provider.issueAccessToken(7L, MemberRole.ADMIN);

            JwtClaims claims = provider.parseAccessToken(token);

            assertThat(claims.role()).isEqualTo(MemberRole.ADMIN);
        }

        @Test
        @DisplayName("만료된 Access Token → AuthException(AUTH_EXPIRED_TOKEN)")
        void expiredToken_throwsAuthException() {
            String token = provider.issueAccessToken(1L, MemberRole.USER);
            JwtProvider laterProvider = new JwtProvider(properties,
                    Clock.fixed(FIXED_NOW.plusSeconds(ACCESS_TTL + 60), ZoneOffset.UTC));

            assertThatThrownBy(() -> laterProvider.parseAccessToken(token))
                    .isInstanceOf(AuthException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.AUTH_EXPIRED_TOKEN);
        }

        @Test
        @DisplayName("위조된 서명(다른 키로 서명) → AuthException(AUTH_INVALID_TOKEN)")
        void forgedToken_throwsAuthException() {
            SecretKey otherKey = Keys.hmacShaKeyFor("a-completely-different-fixture-key-32bytes!".getBytes(StandardCharsets.UTF_8));
            String forged = Jwts.builder()
                    .subject("1")
                    .claim("role", "USER")
                    .claim("typ", "access")
                    .issuedAt(Date.from(FIXED_NOW))
                    .expiration(Date.from(FIXED_NOW.plusSeconds(ACCESS_TTL)))
                    .signWith(otherKey, Jwts.SIG.HS256)
                    .compact();

            assertThatThrownBy(() -> provider.parseAccessToken(forged))
                    .isInstanceOf(AuthException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
        }

        @Test
        @DisplayName("형식이 깨진 토큰 → AuthException(AUTH_INVALID_TOKEN)")
        void malformedToken_throwsAuthException() {
            assertThatThrownBy(() -> provider.parseAccessToken("not-a-jwt"))
                    .isInstanceOf(AuthException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
        }

        @Test
        @DisplayName("null 토큰 → AuthException(AUTH_INVALID_TOKEN)")
        void nullToken_throwsAuthException() {
            assertThatThrownBy(() -> provider.parseAccessToken(null))
                    .isInstanceOf(AuthException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
        }

        @Test
        @DisplayName("빈 문자열 토큰 → AuthException(AUTH_INVALID_TOKEN)")
        void blankToken_throwsAuthException() {
            assertThatThrownBy(() -> provider.parseAccessToken(""))
                    .isInstanceOf(AuthException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
        }

        @Test
        @DisplayName("Refresh Token 을 parseAccessToken 에 전달하면 typ mismatch → AUTH_INVALID_TOKEN")
        void refreshToken_parsedAsAccess_throws() {
            String refresh = provider.issueRefreshToken(1L);

            assertThatThrownBy(() -> provider.parseAccessToken(refresh))
                    .isInstanceOf(AuthException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
        }

        @Test
        @DisplayName("clock skew 30초 이내의 미세한 만료는 허용된다")
        void clockSkew_allowsSmallExpiry() {
            String token = provider.issueAccessToken(1L, MemberRole.USER);
            JwtProvider slightlyLater = new JwtProvider(properties,
                    Clock.fixed(FIXED_NOW.plusSeconds(ACCESS_TTL + 10), ZoneOffset.UTC));

            assertThat(slightlyLater.parseAccessToken(token).memberId()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("Refresh Token 발급/파싱")
    class RefreshToken {

        @Test
        @DisplayName("Refresh Token 발급 후 parseRefreshToken 으로 memberId 추출")
        void issueAndParse_returnsClaims() {
            String token = provider.issueRefreshToken(99L);

            JwtClaims claims = provider.parseRefreshToken(token);

            assertThat(claims.memberId()).isEqualTo(99L);
        }

        @Test
        @DisplayName("Access Token 을 parseRefreshToken 에 전달 → AUTH_INVALID_TOKEN")
        void accessToken_parsedAsRefresh_throws() {
            String access = provider.issueAccessToken(1L, MemberRole.USER);

            assertThatThrownBy(() -> provider.parseRefreshToken(access))
                    .isInstanceOf(AuthException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);
        }
    }
}
