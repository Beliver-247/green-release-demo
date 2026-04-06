package com.greenrelease.api;

import com.greenrelease.api.controller.HealthController;
import com.greenrelease.core.model.HealthStatus;
import com.greenrelease.service.HealthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller tests for health endpoint
 */
@WebMvcTest(HealthController.class)
@ContextConfiguration(classes = TestConfiguration.class)
public class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HealthService healthService;

    @Test
    public void testHealthEndpoint() throws Exception {
        HealthStatus healthStatus = new HealthStatus("UP", "1.0.0");
        when(healthService.getHealth()).thenReturn(healthStatus);

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("UP")))
                .andExpect(jsonPath("$.version", equalTo("1.0.0")));
    }
}
