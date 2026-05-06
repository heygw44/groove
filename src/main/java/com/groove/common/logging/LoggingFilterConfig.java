package com.groove.common.logging;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class LoggingFilterConfig {

    @Bean
    public RequestIdGenerator requestIdGenerator() {
        return RequestIdGenerator.uuid();
    }

    @Bean
    public FilterRegistrationBean<MdcFilter> mdcFilterRegistration(RequestIdGenerator generator) {
        FilterRegistrationBean<MdcFilter> registration = new FilterRegistrationBean<>(new MdcFilter(generator));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        registration.setName("mdcFilter");
        return registration;
    }
}
