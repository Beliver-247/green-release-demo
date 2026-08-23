package com.greenrelease.service;

import com.greenrelease.core.model.HealthStatus;

/**
 * Service for health check endpoint
 */
public class HealthService {

    private static final String APP_VERSION = "1.0.1"; 
    private static final String HEALTHY_STATUS = "UP";

    /**
     * Get application health status
     */
    public HealthStatus getHealth() {
        return new HealthStatus(HEALTHY_STATUS, APP_VERSION);
    }
}
