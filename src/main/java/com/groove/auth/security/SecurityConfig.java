package com.groove.auth.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

/**
 * Stateless API 용 Spring Security 설정.
 *
 * <p>{@link JwtAuthenticationFilter} 가 {@link UsernamePasswordAuthenticationFilter} 앞에 위치해
 * Authorization 헤더의 Bearer 토큰을 검증하고 {@code SecurityContext} 를 채운다.
 * 인증 실패 시 401 은 {@link RestAuthenticationEntryPoint} 가, 권한 부족 403 은
 * {@link RestAccessDeniedHandler} 가 ProblemDetail 형식으로 응답한다.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({CorsProperties.class, JwtProperties.class})
public class SecurityConfig {

    private static final String[] PUBLIC_GET_PATTERNS = {
            "/api/v1/albums/**"
    };

    private static final String[] PUBLIC_PATTERNS = {
            "/api/v1/auth/**",
            "/actuator/health",
            "/error"
    };

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public RestAuthenticationEntryPoint restAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return new RestAuthenticationEntryPoint(objectMapper);
    }

    @Bean
    public RestAccessDeniedHandler restAccessDeniedHandler(ObjectMapper objectMapper) {
        return new RestAccessDeniedHandler(objectMapper);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   RestAuthenticationEntryPoint authenticationEntryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler,
                                                   CorsConfigurationSource corsConfigurationSource,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_PATTERNS).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_PATTERNS).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS 정책을 {@link CorsProperties} 로부터 구성한다.
     *
     * <p>application.yaml 의 default 는 비어 있으므로(모든 origin 차단), profile 별 yaml
     * (local/test/docker) 또는 환경 변수에서 명시적으로 origin 패턴/도메인을 지정해야 한다.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        if (!properties.allowedOriginPatterns().isEmpty()) {
            configuration.setAllowedOriginPatterns(properties.allowedOriginPatterns());
        }
        if (!properties.allowedOrigins().isEmpty()) {
            configuration.setAllowedOrigins(properties.allowedOrigins());
        }
        configuration.setAllowedMethods(properties.allowedMethods());
        configuration.setAllowedHeaders(properties.allowedHeaders());
        configuration.setExposedHeaders(properties.exposedHeaders());
        configuration.setAllowCredentials(properties.allowCredentials());
        configuration.setMaxAge(properties.maxAgeSeconds());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
