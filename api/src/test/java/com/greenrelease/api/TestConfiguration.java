package com.greenrelease.api;

import com.greenrelease.service.HealthService;
import com.greenrelease.service.OrderService;
import com.greenrelease.service.UserService;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * Test configuration for Spring Boot tests
 */
@SpringBootApplication
@ComponentScan(basePackages = {
        "com.greenrelease.api.controller"
})
public class TestConfiguration {

    /**
     * Wire HealthService bean for tests
     */
    @Bean
    public HealthService healthService() {
        return new HealthService();
    }

    /**
     * Wire UserService bean for tests
     */
    @Bean
    public UserService userService() {
        return new UserService();
    }

    /**
     * Wire OrderService bean for tests
     */
    @Bean
    public OrderService orderService() {
        return new OrderService();
    }
}
