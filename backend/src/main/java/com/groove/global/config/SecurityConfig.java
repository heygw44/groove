package com.groove.global.config;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.groove.auth.jwt.JwtAuthenticationFilter;
import com.groove.auth.jwt.JwtProvider;

import lombok.RequiredArgsConstructor;

/**
 * 기본 보안 설정.
 * - 무상태(STATELESS), CSRF/폼로그인/HTTP Basic 비활성화
 * - 공개 경로: 헬스체크, 회원가입/로그인/재발급, Swagger, Actuator health
 * - 인증 실패 401 / 권한 없음 403 은 공통 ApiResponse JSON 으로 응답
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({JwtProperties.class, AuthCookieProperties.class})
@RequiredArgsConstructor
public class SecurityConfig {

	private final RestAuthenticationEntryPoint authenticationEntryPoint;
	private final RestAccessDeniedHandler accessDeniedHandler;
	private final JwtProvider jwtProvider;

	private static final String[] PUBLIC_PATHS = {
		"/api/v1/health",
		"/api/v1/time",
		"/api/v1/auth/signup",
		"/api/v1/auth/login",
		"/api/v1/auth/reissue",
		"/actuator/health",
		"/swagger-ui.html",
		"/swagger-ui/**",
		"/v3/api-docs/**",
		"/uploads/**"
	};

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.formLogin(form -> form.disable())
				.httpBasic(basic -> basic.disable())
				.cors(withDefaults())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint(authenticationEntryPoint)
						.accessDeniedHandler(accessDeniedHandler))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(PUBLIC_PATHS).permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/genres", "/api/v1/labels", "/api/v1/artists/**")
								.permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/limited-drops", "/api/v1/limited-drops/**")
								.permitAll()
						.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.POST, "/api/v1/files/**").hasRole("ADMIN")
						.anyRequest().authenticated())
				.addFilterBefore(new JwtAuthenticationFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
