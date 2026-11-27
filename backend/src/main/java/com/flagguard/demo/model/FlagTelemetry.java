package com.flagguard.demo.model;

public record FlagTelemetry(
        String flagName,
        boolean enabled,
        long totalRequests,
        long failedRequests,
        double errorRate,
        double avgLatencyMs,
        double maxErrorThreshold,
        long maxLatencyThresholdMs,
        String status
) {}
