package com.greenrelease.app;

import com.greenrelease.core.model.HealthStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = GreenReleaseDemoApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthControllerIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldReturnUpStatusAndVersion() {
        ResponseEntity<HealthStatus> response = restTemplate.getForEntity("/health", HealthStatus.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UP", response.getBody().getStatus());
        assertEquals("1.0.2", response.getBody().getVersion());
    }
}
