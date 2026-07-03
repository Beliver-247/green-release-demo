package com.greenrelease.app;

import com.greenrelease.service.HealthService;
import com.greenrelease.service.OrderService;
import com.greenrelease.service.UserService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot application entry point
 * Configures all modules and wires dependencies
 */
//comment to test ML model
@SpringBootApplication
@ComponentScan(basePackages = {
        "com.greenrelease.api.controller",
        "com.greenrelease.app"
})
public class GreenReleaseDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(GreenReleaseDemoApplication.class, args);
    }

    /**
     * Wire HealthService bean
     */
    @Bean
    public HealthService healthService() {
        return new HealthService();
    } 

    /**
     * Wire UserService bean
     */
    @Bean
    public UserService userService() {
        return new UserService();
    }

    /**
     * Wire OrderService bean
     */
    @Bean
    public OrderService orderService() {
        return new OrderService();
    }
}
