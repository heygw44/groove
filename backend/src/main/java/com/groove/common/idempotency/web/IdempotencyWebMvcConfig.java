package com.groove.common.idempotency.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * {@link IdempotencyKeyInterceptor} 를 MVC 인터셉터 체인에 등록한다.
 *
 * <p>모든 경로에 매핑하지만 인터셉터는 {@code @Idempotent} 핸들러에서만 동작하므로 다른 엔드포인트에는
 * 영향이 없다.
 */
@Configuration(proxyBeanMethods = false)
public class IdempotencyWebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new IdempotencyKeyInterceptor()).addPathPatterns("/**");
    }
}
