package com.groove.auth.security;

import com.groove.auth.security.ratelimit.AuthRateLimitProperties;
import com.groove.web.SpaRoutes;
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
 * Stateless API 용 Spring Security 설정. JwtAuthenticationFilter 가
 * UsernamePasswordAuthenticationFilter 앞에서 Bearer 토큰을 검증하고 SecurityContext 를 채운다.
 * 인증 실패 401 은 RestAuthenticationEntryPoint, 권한 부족 403 은 RestAccessDeniedHandler 가
 * ProblemDetail 로 응답한다.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({CorsProperties.class, JwtProperties.class, AuthRateLimitProperties.class, RefreshCookieProperties.class})
public class SecurityConfig {

    private static final String[] PUBLIC_GET_PATTERNS = {
            "/api/v1/albums/**",
            "/api/v1/artists/**",
            "/api/v1/genres/**",
            "/api/v1/labels/**",
            "/api/v1/shippings/**",
            // 발급 가능 쿠폰 목록만 공개. 와일드카드 없는 정확 경로라 하위 경로는 매칭되지 않는다.
            "/api/v1/coupons",
            // 토스 successUrl/failUrl 브라우저 리다이렉트 타깃(#295). 인증 없이 confirm/보상 처리 후 SPA 로 302.
            "/payments/toss/success",
            "/payments/toss/fail"
    };

    /**
     * 정적 SPA 프론트엔드 빌드 산출물 경로. GET 으로만 공개한다.
     */
    private static final String[] PUBLIC_STATIC_GET_PATTERNS = {
            "/",
            "/index.html",
            "/favicon.ico",
            "/favicon.svg",
            "/css/**",
            "/js/**",
            "/assets/**"
    };

    private static final String[] PUBLIC_PATTERNS = {
            "/api/v1/auth/**",
            "/actuator/health",
            "/error"
    };

    /**
     * SpringDoc OpenAPI/Swagger UI 정적 문서 경로. 인증 없이 공개한다.
     */
    private static final String[] SWAGGER_PATTERNS = {
            "/v3/api-docs/**",
            "/v3/api-docs.yaml",
            "/swagger-ui/**",
            "/swagger-ui.html",
            // swagger-ui webjar 자산만 공개.
            "/webjars/swagger-ui/**"
    };

    /** permitAll 로 여는 POST 진입점 (게스트 주문 생성·조회, 결제 요청, 토스 checkout, PG 웹훅·토스 웹훅). */
    private static final String[] PUBLIC_POST_PATTERNS = {
            "/api/v1/orders",
            "/api/v1/orders/*/guest-lookup",
            "/api/v1/payments",
            "/api/v1/payments/toss/checkout",
            "/api/v1/payments/webhook",
            "/api/v1/payments/toss/webhook"
    };

    private static final String ADMIN_PATTERN = "/api/v1/admin/**";

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
                        .requestMatchers(HttpMethod.GET, PUBLIC_STATIC_GET_PATTERNS).permitAll()
                        // SPA clean-route 의 HTML 셸만 GET 공개.
                        .requestMatchers(HttpMethod.GET, SpaRoutes.PATTERNS).permitAll()
                        .requestMatchers(PUBLIC_PATTERNS).permitAll()
                        .requestMatchers(SWAGGER_PATTERNS).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_PATTERNS).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_PATTERNS).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(ADMIN_PATTERN).hasRole("ADMIN")
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
     * CORS 정책을 CorsProperties 로부터 구성한다.
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
