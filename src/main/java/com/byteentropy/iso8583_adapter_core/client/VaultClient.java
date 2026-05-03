package com.byteentropy.iso8583_adapter_core.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Properties;

public class VaultClient {
    private static final Logger logger = LoggerFactory.getLogger(VaultClient.class);
    private final HttpClient httpClient;
    private final String vaultUrl;
    private final int timeoutMs;

    public VaultClient(Properties props) {
        this.vaultUrl = props.getProperty("adapter.vault.url", "http://localhost:8081/v1/process");
        this.timeoutMs = Integer.parseInt(props.getProperty("adapter.vault.timeout-ms", "120"));
        
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    public String tokenize(String payload) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(vaultUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .timeout(Duration.ofMillis(timeoutMs))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                logger.error("Vault service returned HTTP {}", response.statusCode());
                throw new RuntimeException("Vault error: " + response.statusCode());
            }
            return response.body();
        } catch (Exception e) {
            logger.error("Vault communication failure: {}", e.getMessage());
            throw e;
        }
    }
}