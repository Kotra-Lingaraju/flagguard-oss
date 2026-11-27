package com.flagguard.demo.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class FlagsmithGateway {
    private static final Logger log = LoggerFactory.getLogger(FlagsmithGateway.class);

    @Value("${flagguard.flagsmith-url}")
    private String flagsmithUrl;

    @Value("${flagguard.client-key}")
    private String clientKey;

    @Value("${flagguard.server-key}")
    private String serverKey;

    @Value("${flagguard.org-api-key}")
    private String orgApiKey;

    private RestClient sdkRestClient;
    private RestClient managementClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        this.sdkRestClient = RestClient.builder()
                .baseUrl(flagsmithUrl)
                .defaultHeader("X-Environment-Key", serverKey.trim())
                .build();

        this.managementClient = RestClient.builder()
                .baseUrl(flagsmithUrl)
                .defaultHeader("Authorization", "Api-Key " + orgApiKey.trim())
                .build();
    }

    public boolean isFeatureEnabled(String flagName) {
        try {
            String flagsJson = sdkRestClient.get()
                    .uri("/api/v1/flags/")
                    .retrieve()
                    .body(String.class);

            JsonNode array = mapper.readTree(flagsJson);
            for (JsonNode item : array) {
                String name = item.path("feature").path("name").asText();
                if (flagName.equals(name)) {
                    return item.path("enabled").asBoolean(false);
                }
            }
        } catch (Exception e) {
            log.warn("Flagsmith SDK evaluation failed for {}: {}", flagName, e.getMessage());
        }
        return false;
    }

    public boolean setFeatureEnabled(String flagName, boolean enabled) {
        try {
            String featuresJson = managementClient.get()
                    .uri("/api/v1/environments/{envKey}/featurestates/", clientKey.trim())
                    .retrieve()
                    .body(String.class);

            JsonNode results = mapper.readTree(featuresJson).path("results");
            int projectId = resolveProjectId();
            for (JsonNode node : results) {
                int featureId = node.path("feature").asInt();
                String featureDetailJson = managementClient.get()
                        .uri("/api/v1/projects/{projectId}/features/{id}/", projectId, featureId)
                        .retrieve()
                        .body(String.class);

                String name = mapper.readTree(featureDetailJson).path("name").asText();
                if (flagName.equals(name)) {
                    int featureStateId = node.path("id").asInt();
                    managementClient.patch()
                            .uri("/api/v1/environments/{envKey}/featurestates/{id}/", clientKey.trim(), featureStateId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(Map.of("enabled", enabled))
                            .retrieve()
                            .toBodilessEntity();
                    log.info("Flagsmith Management API updated flag '{}' -> enabled={}", flagName, enabled);
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("Failed to toggle flag '{}' via Flagsmith Management API: {}", flagName, e.getMessage());
        }
        return false;
    }

    private int resolveProjectId() throws Exception {
        String envJson = managementClient.get()
                .uri("/api/v1/environments/{envKey}/", clientKey.trim())
                .retrieve()
                .body(String.class);
        return mapper.readTree(envJson).path("project").asInt(1);
    }
}
