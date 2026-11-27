package com.flagguard.demo.controller;

import com.flagguard.demo.service.CheckoutService;
import com.flagguard.demo.service.GuardEngineService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class FlagGuardController {

    private final CheckoutService checkoutService;
    private final GuardEngineService guardEngine;

    public FlagGuardController(CheckoutService checkoutService, GuardEngineService guardEngine) {
        this.checkoutService = checkoutService;
        this.guardEngine = guardEngine;
    }

    @PostMapping("/checkout")
    public ResponseEntity<Object> checkout(@RequestParam(value = "amount", defaultValue = "49.99") double amount) {
        try {
            return ResponseEntity.ok(checkoutService.processOrder(amount));
        } catch (Exception ex) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("engine", "express-checkout-v2");
            err.put("status", "FAILED");
            err.put("error", ex.getMessage() != null ? ex.getMessage() : "Checkout error");
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(err);
        }
    }

    @PostMapping("/chaos")
    public Map<String, Object> toggleChaos(@RequestParam("enabled") boolean enabled) {
        boolean state = checkoutService.setChaos(enabled);
        guardEngine.broadcastState();
        return Map.of("chaosEnabled", state);
    }

    @PostMapping("/flags/{flagName}/re-enable")
    public Map<String, Object> reEnable(@PathVariable("flagName") String flagName) {
        checkoutService.setChaos(false);
        boolean ok = guardEngine.reEnableFlag(flagName);
        return Map.of("flagName", flagName, "reEnabled", ok);
    }

    @GetMapping("/state")
    public Map<String, Object> getState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("flags", guardEngine.getTelemetrySnapshot());
        state.put("incidents", guardEngine.getIncidents());
        state.put("chaosEnabled", checkoutService.isChaosEnabled());
        return state;
    }

    @GetMapping(value = "/events/flags", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamFlags() {
        return guardEngine.subscribe();
    }
}
