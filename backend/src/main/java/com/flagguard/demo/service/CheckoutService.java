package com.flagguard.demo.service;

import com.flagguard.demo.annotation.FlagGuarded;
import com.flagguard.demo.client.FlagsmithGateway;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class CheckoutService {
    private final FlagsmithGateway flagsmith;
    private final AtomicBoolean chaosEnabled = new AtomicBoolean(false);

    public CheckoutService(FlagsmithGateway flagsmith) {
        this.flagsmith = flagsmith;
    }

    @FlagGuarded(flag = "checkout-v2", maxErrorRate = 0.15, maxLatencyMs = 800)
    public Map<String, Object> processOrder(double amount) {
        if (!flagsmith.isFeatureEnabled("checkout-v2")) {
            return Map.of(
                    "engine", "legacy-checkout-v1",
                    "status", "SUCCESS",
                    "amount", amount,
                    "message", "Processed safely via fallback v1 engine"
            );
        }

        if (chaosEnabled.get()) {
            throw new RuntimeException("Simulated Payment Gateway 502 Timeout in checkout-v2!");
        }

        return Map.of(
                "engine", "express-checkout-v2",
                "status", "SUCCESS",
                "amount", amount,
                "message", "Processed via Flagsmith-enabled v2 engine"
        );
    }

    public boolean setChaos(boolean enabled) {
        chaosEnabled.set(enabled);
        return chaosEnabled.get();
    }

    public boolean isChaosEnabled() {
        return chaosEnabled.get();
    }
}