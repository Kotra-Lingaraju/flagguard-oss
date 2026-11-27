package com.flagguard.demo.service;

import com.flagguard.demo.client.FlagsmithGateway;
import com.flagguard.demo.model.FlagTelemetry;
import com.flagguard.demo.model.RollbackIncident;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class GuardEngineService {
    private static final Logger log = LoggerFactory.getLogger(GuardEngineService.class);

    private final FlagsmithGateway flagsmith;
    private final Map<String, FlagWindow> windows = new ConcurrentHashMap<>();
    private final List<RollbackIncident> incidents = new CopyOnWriteArrayList<>();
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    @Value("${flagguard.min-sample-size:5}")
    private int minSampleSize;

    public GuardEngineService(FlagsmithGateway flagsmith) {
        this.flagsmith = flagsmith;
    }

    public void registerGuard(String flagName, double maxErrorRate, long maxLatencyMs) {
        windows.computeIfAbsent(flagName, k -> new FlagWindow(maxErrorRate, maxLatencyMs));
    }

    public void recordOutcome(String flagName, boolean success, long latencyMs) {
        FlagWindow w = windows.get(flagName);
        if (w == null) return;
        w.total.incrementAndGet();
        if (!success) w.failed.incrementAndGet();
        w.totalLatencyMs.addAndGet(latencyMs);
        broadcastState();
    }

    @Scheduled(fixedRateString = "${flagguard.watchdog-interval-ms:3000}")
    public void evaluateHealth() {
        for (Map.Entry<String, FlagWindow> entry : windows.entrySet()) {
            String flagName = entry.getKey();
            FlagWindow w = entry.getValue();
            long total = w.total.get();
            if (total < minSampleSize) continue;

            boolean currentlyEnabled = flagsmith.isFeatureEnabled(flagName);
            if (!currentlyEnabled) continue;

            double errorRate = (double) w.failed.get() / total;
            double avgLatency = (double) w.totalLatencyMs.get() / total;

            if (errorRate > w.maxErrorRate || avgLatency > w.maxLatencyMs) {
                String reason = errorRate > w.maxErrorRate
                        ? String.format("Error rate %.1f%% exceeded %.1f%% threshold", errorRate * 100, w.maxErrorRate * 100)
                        : String.format("Avg latency %.0fms exceeded %dms threshold", avgLatency, w.maxLatencyMs);

                log.warn("KILL-SWITCH TRIGGERED for '{}': {}", flagName, reason);
                boolean disabled = flagsmith.setFeatureEnabled(flagName, false);
                if (disabled) {
                    incidents.add(0, new RollbackIncident(
                            UUID.randomUUID().toString().substring(0, 8),
                            flagName,
                            reason,
                            errorRate,
                            avgLatency,
                            "AUTO_DISABLED_IN_FLAGSMITH",
                            Instant.now()
                    ));
                    broadcastState();
                }
            }
        }
    }

    public boolean reEnableFlag(String flagName) {
        FlagWindow w = windows.get(flagName);
        if (w != null) {
            w.reset();
        }
        boolean ok = flagsmith.setFeatureEnabled(flagName, true);
        broadcastState();
        return ok;
    }

    public List<FlagTelemetry> getTelemetrySnapshot() {
        if (windows.isEmpty()) {
            registerGuard("checkout-v2", 0.15, 800);
        }
        return windows.entrySet().stream().map(e -> {
            String name = e.getKey();
            FlagWindow w = e.getValue();
            long total = w.total.get();
            long failed = w.failed.get();
            double errRate = total == 0 ? 0.0 : (double) failed / total;
            double avgLat = total == 0 ? 0.0 : (double) w.totalLatencyMs.get() / total;
            boolean enabled = flagsmith.isFeatureEnabled(name);
            String status = !enabled ? "AUTO_ROLLED_BACK" : (errRate > w.maxErrorRate * 0.7 ? "DEGRADED" : "HEALTHY");
            return new FlagTelemetry(name, enabled, total, failed, errRate, avgLat, w.maxErrorRate, w.maxLatencyMs, status);
        }).toList();
    }

    public List<RollbackIncident> getIncidents() {
        return List.copyOf(incidents);
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    public void broadcastState() {
        Map<String, Object> payload = Map.of(
                "flags", getTelemetrySnapshot(),
                "incidents", getIncidents(),
                "updatedAt", Instant.now().toString()
        );
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("flag-update").data(payload));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }

    private static class FlagWindow {
        final double maxErrorRate;
        final long maxLatencyMs;
        final AtomicLong total = new AtomicLong();
        final AtomicLong failed = new AtomicLong();
        final AtomicLong totalLatencyMs = new AtomicLong();

        FlagWindow(double maxErrorRate, long maxLatencyMs) {
            this.maxErrorRate = maxErrorRate;
            this.maxLatencyMs = maxLatencyMs;
        }

        void reset() {
            total.set(0);
            failed.set(0);
            totalLatencyMs.set(0);
        }
    }
}