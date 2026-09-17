package com.expensetracker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class KeepAliveService {

    private static final Logger log = LoggerFactory.getLogger(KeepAliveService.class);

    @Value("${app.keepalive.url:https://expense-tracker-ai-c9f0.onrender.com/ping}")
    private String pingUrl;

    @Value("${app.keepalive.enabled:true}")
    private boolean enabled;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    // Runs every 12 minutes (720,000 ms), starting 60 seconds after application startup
    @Scheduled(initialDelay = 60_000, fixedRate = 720_000)
    public void keepAliveTrigger() {
        if (!enabled || pingUrl == null || pingUrl.isBlank()) {
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pingUrl))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .header("User-Agent", "ExpenseAI-KeepAlive-Trigger/1.0")
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .thenAccept(response -> {
                        if (response.statusCode() >= 200 && response.statusCode() < 400) {
                            log.info("[KeepAlive] Triggered ping to {} -> HTTP {} (Warm)", pingUrl, response.statusCode());
                        } else {
                            log.warn("[KeepAlive] Ping to {} returned status {}", pingUrl, response.statusCode());
                        }
                    })
                    .exceptionally(ex -> {
                        log.debug("[KeepAlive] Ping trigger: {}", ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            log.debug("[KeepAlive] Unable to trigger ping to {}: {}", pingUrl, e.getMessage());
        }
    }
}
