package com.groove.common.idempotency.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** IdempotencyKeyInterceptor 를 MVC 인터셉터 체인에 모든 경로로 등록한다. */
@Configuration(proxyBeanMethods = false)
public class IdempotencyWebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new IdempotencyKeyInterceptor()).addPathPatterns("/**");
    }
}
