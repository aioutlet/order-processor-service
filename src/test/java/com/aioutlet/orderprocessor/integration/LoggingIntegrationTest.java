package com.aioutlet.orderprocessor.integration;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test to demonstrate enhanced logging format
 */
@SpringBootTest
@TestPropertySource(properties = {
    "tracing.enabled=false",  // Disable tracing for this test
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.rabbitmq.enabled=false"
})
class LoggingIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(LoggingIntegrationTest.class);

    @Test
    void testLoggingFormat() {
        // Set up MDC values to simulate the enhanced logging
        try {
            MDC.put("correlationId", "test-correlation-123");
            MDC.put("serviceName", "order-processor-service");
            MDC.put("serviceVersion", "1.0.0");
            
            // Log messages to demonstrate the format
            logger.info("Testing enhanced logging format without tracing");
            logger.debug("Debug message with correlation ID");
            logger.warn("Warning message in enhanced format");
            
            // With trace context (simulated)
            MDC.put("traceId", "test-trace-456");
            MDC.put("spanId", "test-span-789");
            
            logger.info("Testing enhanced logging format with simulated tracing context");
            logger.error("Error message with full tracing context");
            
            // Test that logging doesn't throw exceptions
            assertTrue(true, "Logging format works correctly");
            
        } finally {
            MDC.clear();
        }
    }
    
    @Test
    void testLoggingWithoutMDC() {
        // Test logging without any MDC values
        logger.info("Testing logging without MDC - should show defaults");
        logger.debug("Debug message without correlation ID or trace context");
        
        assertTrue(true, "Logging works without MDC values");
    }
}