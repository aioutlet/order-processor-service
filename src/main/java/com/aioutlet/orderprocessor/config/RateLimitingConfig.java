package com.aioutlet.orderprocessor.config;

import com.aioutlet.orderprocessor.filter.RateLimitFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for rate limiting in Order Processor Service
 */
@Configuration
public class RateLimitingConfig {

    /**
     * Register the rate limiting filter
     */
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter rateLimitFilter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(rateLimitFilter);
        registration.addUrlPatterns("/*");
        registration.setName("rateLimitFilter");
        registration.setOrder(2); // After correlation ID filter
        return registration;
    }
}
