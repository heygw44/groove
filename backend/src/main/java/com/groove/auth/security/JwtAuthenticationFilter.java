package com.groove.auth.security;

import com.groove.common.exception.AuthException;
import com.groove.security.AuthPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 매 요청마다 Authorization 헤더의 Bearer 토큰을 검증해 SecurityContextHolder 를 채운다.
 * 토큰이 없거나 검증에 실패하면 SecurityContext 를 비워두고 다음 필터로 진행한다.
 * Bearer prefix 매칭은 case-insensitive 이며, 인증 실패 사유는 reason 만 WARN 으로 기록한다.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int BEARER_PREFIX_LENGTH = BEARER_PREFIX.length();

    private final JwtProvider jwtProvider;

    public JwtAuthenticationFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String token = extractBearerToken(request);
        if (token != null) {
            try {
                JwtClaims claims = jwtProvider.parseAccessToken(token);
                authenticate(claims);
            } catch (AuthException ex) {
                SecurityContextHolder.clearContext();
                log.warn("jwt.auth.failure path={} reason={}", request.getRequestURI(), ex.getErrorCode().getCode());
            }
        }
        filterChain.doFilter(request, response);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || header.length() < BEARER_PREFIX_LENGTH) {
            return null;
        }
        if (!header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX_LENGTH)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX_LENGTH).trim();
        return token.isEmpty() ? null : token;
    }

    private void authenticate(JwtClaims claims) {
        AuthPrincipal principal = new AuthPrincipal(claims.memberId(), claims.role().name());
        var authority = new SimpleGrantedAuthority("ROLE_" + claims.role().name());
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(authority));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
