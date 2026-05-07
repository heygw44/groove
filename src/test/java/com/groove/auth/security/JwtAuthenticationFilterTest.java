package com.groove.auth.security;

import com.groove.member.domain.MemberRole;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("JwtAuthenticationFilter")
class JwtAuthenticationFilterTest {

    private static final String FIXTURE_HMAC_BYTES = "fixture-hmac-bytes-must-be-at-least-32-bytes-long-for-hs256!";
    private static final Instant FIXED_NOW = Instant.parse("2026-05-07T10:00:00Z");

    private JwtProvider provider;
    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(FIXTURE_HMAC_BYTES, 1800L, 1209600L);
        Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        provider = new JwtProvider(properties, clock);
        filter = new JwtAuthenticationFilter(provider);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Authorization 헤더 없음 → SecurityContext 비어 있고 다음 필터로 진행")
    void noHeader_passesThrough() throws Exception {
        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("유효한 Bearer 토큰 → SecurityContext 에 인증 객체 주입")
    void validToken_setsAuthentication() throws Exception {
        String token = provider.issueAccessToken(42L, MemberRole.USER);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        filter.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.isAuthenticated()).isTrue();
        assertThat(auth.getPrincipal()).isInstanceOf(AuthPrincipal.class);
        AuthPrincipal principal = (AuthPrincipal) auth.getPrincipal();
        assertThat(principal.memberId()).isEqualTo(42L);
        assertThat(principal.role()).isEqualTo(MemberRole.USER);
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("ADMIN 토큰 → ROLE_ADMIN 권한 부여")
    void adminToken_grantsRoleAdmin() throws Exception {
        String token = provider.issueAccessToken(1L, MemberRole.ADMIN);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        filter.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("Bearer prefix 대소문자 무시 (RFC 7235) — bearer/BEARER 모두 허용")
    void bearerPrefix_caseInsensitive() throws Exception {
        String token = provider.issueAccessToken(1L, MemberRole.USER);
        request.addHeader(HttpHeaders.AUTHORIZATION, "bearer " + token);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    @DisplayName("Bearer 가 아닌 스킴(Token) → SecurityContext 비어 있음 (무시)")
    void nonBearerScheme_isIgnored() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Token abc.def.ghi");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("위조 토큰 → SecurityContext 비어 있고 chain 진행 (EntryPoint 가 401 처리)")
    void invalidToken_clearsContextAndContinues() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("만료 토큰 → SecurityContext 비어 있고 chain 진행")
    void expiredToken_clearsContextAndContinues() throws Exception {
        String token = provider.issueAccessToken(1L, MemberRole.USER);
        // 새 필터 인스턴스 with later clock
        JwtProvider laterProvider = new JwtProvider(
                new JwtProperties(FIXTURE_HMAC_BYTES, 1800L, 1209600L),
                Clock.fixed(FIXED_NOW.plusSeconds(99999L), ZoneOffset.UTC));
        JwtAuthenticationFilter laterFilter = new JwtAuthenticationFilter(laterProvider);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        laterFilter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain, times(1)).doFilter(request, response);
    }
}
