package com.groove.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SPA(Vue Router history 모드) clean URL fallback.
 *
 * <p>SpaRoutes.PATTERNS 의 경로를 index.html 로 forward 한다.
 */
@Configuration(proxyBeanMethods = false)
public class SpaForwardConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        for (String pattern : SpaRoutes.PATTERNS) {
            registry.addViewController(pattern).setViewName("forward:/index.html");
        }
    }
}
