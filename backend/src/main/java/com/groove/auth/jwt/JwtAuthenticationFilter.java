package com.groove.auth.jwt;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.groove.auth.LoginMember;
import com.groove.global.common.BusinessException;
import com.groove.global.config.RestAuthenticationEntryPoint;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authorization 헤더의 Access Token 을 검증해 SecurityContext 를 채운다.
 * 검증 실패는 예외를 던지지 않고 request attribute 에 원인 코드를 남긴 뒤 체인을 계속 진행한다.
 * 보호된 경로라면 이후 ExceptionTranslationFilter → RestAuthenticationEntryPoint 가 그 코드를 읽어 응답한다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String AUTHORIZATION_HEADER = "Authorization";
	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtProvider jwtProvider;

	public JwtAuthenticationFilter(JwtProvider jwtProvider) {
		this.jwtProvider = jwtProvider;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
		throws ServletException, IOException {
		String token = resolveToken(request);
		if (token == null) {
			filterChain.doFilter(request, response);
			return;
		}

		try {
			authenticate(token);
		} catch (BusinessException e) {
			SecurityContextHolder.getContextHolderStrategy().clearContext();
			request.setAttribute(RestAuthenticationEntryPoint.ERROR_CODE_ATTRIBUTE, e.getErrorCode());
		}

		filterChain.doFilter(request, response);
	}

	private String resolveToken(HttpServletRequest request) {
		String header = request.getHeader(AUTHORIZATION_HEADER);
		if (header != null && header.startsWith(BEARER_PREFIX)) {
			return header.substring(BEARER_PREFIX.length());
		}
		return null;
	}

	private void authenticate(String token) {
		TokenClaims claims = jwtProvider.parseAccessToken(token);
		LoginMember loginMember = new LoginMember(claims.memberId(), claims.role());
		List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(claims.role().authority()));
		Authentication authentication = new UsernamePasswordAuthenticationToken(loginMember, null, authorities);

		SecurityContext context = SecurityContextHolder.getContextHolderStrategy().createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.getContextHolderStrategy().setContext(context);
	}
}
