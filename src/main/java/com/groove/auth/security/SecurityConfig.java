package com.groove.auth.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

/**
 * Stateless API 용 Spring Security 베이스라인.
 *
 * <p>이번 단계에서는 보호/공개 엔드포인트 분리만 활성화한다. JWT 인증 필터는 W4(#W4-2) 에서
 * {@code addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)}
 * 로 연결될 자리만 마련한다.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

    private static final String[] PUBLIC_GET_PATTERNS = {
            "/api/v1/albums/**"
    };

    private static final String[] PUBLIC_PATTERNS = {
            "/auth/**",
            "/actuator/health",
            "/error"
    };

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
                                                   CorsConfigurationSource corsConfigurationSource) throws Exception {
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
                );

        // TODO(W4-2): JWT 인증 필터 연결.
        // http.addFilterBefore(jwtAuthenticationFilter,
        //         org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);

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
