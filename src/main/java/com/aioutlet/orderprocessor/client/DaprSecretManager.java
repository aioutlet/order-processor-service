package com.aioutlet.orderprocessor.client;

import io.dapr.client.DaprClient;
import io.dapr.client.DaprClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Map;

/**
 * Dapr Secret Manager
 * Handles retrieving secrets from Dapr secret store
 */
@Service
@Slf4j
public class DaprSecretManager {

    @Value("${dapr.secret-store.name:local-secret-store}")
    private String secretStoreName;

    private DaprClient daprClient;

    @PostConstruct
    public void init() {
        this.daprClient = new DaprClientBuilder().build();
        log.info("Dapr Secret Manager initialized with store: {}", secretStoreName);
    }

    @PreDestroy
    public void cleanup() {
        if (daprClient != null) {
            try {
                daprClient.close();
                log.info("Dapr client closed successfully");
            } catch (Exception e) {
                log.error("Error closing Dapr client", e);
            }
        }
    }

    /**
     * Get a specific secret by key
     */
    public String getSecret(String key) {
        try {
            log.debug("Retrieving secret: {}", key);
            
            Map<String, String> secret = daprClient.getSecret(secretStoreName, key).block();
            
            if (secret == null || secret.isEmpty()) {
                log.warn("Secret not found: {}", key);
                return null;
            }
            
            return secret.get(key);
        } catch (Exception e) {
            log.error("Failed to retrieve secret: {}", key, e);
            throw new RuntimeException("Failed to retrieve secret", e);
        }
    }

    /**
     * Get all secrets for a specific key (returns all metadata)
     */
    public Map<String, String> getSecrets(String key) {
        try {
            log.debug("Retrieving secrets for key: {}", key);
            return daprClient.getSecret(secretStoreName, key).block();
        } catch (Exception e) {
            log.error("Failed to retrieve secrets for key: {}", key, e);
            throw new RuntimeException("Failed to retrieve secrets", e);
        }
    }

    /**
     * Get database configuration from secrets
     */
    public DatabaseConfig getDatabaseConfig() {
        String host = getSecret("DATABASE_HOST");
        String port = getSecret("DATABASE_PORT");
        String name = getSecret("DATABASE_NAME");
        String user = getSecret("DATABASE_USER");
        String password = getSecret("DATABASE_PASSWORD");
        
        return new DatabaseConfig(host, port, name, user, password);
    }

    /**
     * Get JWT secret
     */
    public String getJwtSecret() {
        return getSecret("JWT_SECRET");
    }

    /**
     * Get service URLs
     */
    public ServiceUrls getServiceUrls() {
        String orderService = getSecret("ORDER_SERVICE_URL");
        String paymentService = getSecret("PAYMENT_SERVICE_URL");
        String inventoryService = getSecret("INVENTORY_SERVICE_URL");
        String shippingService = getSecret("SHIPPING_SERVICE_URL");
        
        return new ServiceUrls(orderService, paymentService, inventoryService, shippingService);
    }

    // Inner classes for structured configuration
    public record DatabaseConfig(
        String host,
        String port,
        String name,
        String user,
        String password
    ) {
        public String getJdbcUrl() {
            return String.format("jdbc:postgresql://%s:%s/%s", host, port, name);
        }
    }

    public record ServiceUrls(
        String orderService,
        String paymentService,
        String inventoryService,
        String shippingService
    ) {}
}
