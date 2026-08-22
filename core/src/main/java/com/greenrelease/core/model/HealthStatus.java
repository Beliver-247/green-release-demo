package com.greenrelease.core.model;

/**
 * Health status model - static response structure
 */
public class HealthStatus {
    private final String status;
    private final String version;

    public HealthStatus(String status, String version) {
        this.status = status;
        this.version = version;
    }

    public String getStatus() {
        return status;
    }

    public String getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "HealthStatus{" +
                "status='" + status + '\'' +
                ", version='" + version + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HealthStatus)) return false;

        HealthStatus that = (HealthStatus) o;

        if (!status.equals(that.status)) return false;
        return version.equals(that.version);
    }

    @Override
    public int hashCode() {
        int result = status.hashCode();
        result = 31 * result + version.hashCode();
        return result;
    }
}
