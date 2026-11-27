package com.flagguard.demo.model;

import java.time.Instant;

public record RollbackIncident(
        String id,
        String flagName,
        String reason,
        double observedErrorRate,
        double observedAvgLatencyMs,
        String actionTaken,
        Instant timestamp
) {}
