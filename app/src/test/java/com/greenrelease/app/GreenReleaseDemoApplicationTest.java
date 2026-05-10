package com.greenrelease.app;

import com.greenrelease.service.HealthService;
import com.greenrelease.service.OrderService;
import com.greenrelease.service.UserService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class GreenReleaseDemoApplicationTest {

    private final GreenReleaseDemoApplication application = new GreenReleaseDemoApplication();

    @Test
    void healthServiceBeanIsCreated() {
        HealthService healthService = application.healthService();
        assertNotNull(healthService);
    }

    @Test
    void userServiceBeanIsCreated() {
        UserService userService = application.userService();
        assertNotNull(userService);
    }

    @Test
    void orderServiceBeanIsCreated() {
        OrderService orderService = application.orderService();
        assertNotNull(orderService);
    }
}
