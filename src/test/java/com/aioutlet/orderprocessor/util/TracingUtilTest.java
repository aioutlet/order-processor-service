package com.aioutlet.orderprocessor.util;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TracingUtil
 */
class TracingUtilTest {

    private TracingUtil tracingUtil;

    @BeforeEach
    void setUp() {
        // Create TracingUtil with no-op OpenTelemetry for testing
        tracingUtil = new TracingUtil(OpenTelemetry.noop(), "test-service", "1.0.0", false);
    }

    @Test
    void getTracingContext_WhenTracingDisabled_ShouldReturnEmpty() {
        TracingUtil.TracingContext context = tracingUtil.getTracingContext();
        
        assertNotNull(context);
        assertFalse(context.isValid());
        assertNull(context.getTraceId());
        assertNull(context.getSpanId());
    }

    @Test
    void getServiceInfo_ShouldReturnCorrectServiceInfo() {
        TracingUtil.ServiceInfo serviceInfo = tracingUtil.getServiceInfo();
        
        assertNotNull(serviceInfo);
        assertEquals("test-service", serviceInfo.getServiceName());
        assertEquals("1.0.0", serviceInfo.getServiceVersion());
    }

    @Test
    void createOperationSpan_WhenTracingDisabled_ShouldReturnNoOpSpan() {
        TracingUtil.OperationSpan span = tracingUtil.createOperationSpan("test-operation");
        
        assertNotNull(span);
        assertNull(span.getTraceId());
        assertNull(span.getSpanId());
        
        // Should not throw exceptions
        span.end();
        span.setStatus(io.opentelemetry.api.trace.StatusCode.OK, "test");
        span.addEvent("test-event");
        span.setAttribute("test-key", "test-value");
    }

    @Test
    void isTracingEnabled_WhenTracingDisabled_ShouldReturnFalse() {
        assertFalse(tracingUtil.isTracingEnabled());
    }

    @Test
    void tracingContext_ToString_ShouldFormatCorrectly() {
        TracingUtil.TracingContext context = new TracingUtil.TracingContext("trace123", "span456");
        
        String result = context.toString();
        assertTrue(result.contains("trace123"));
        assertTrue(result.contains("span456"));
    }

    @Test
    void tracingContext_Empty_ShouldNotBeValid() {
        TracingUtil.TracingContext context = TracingUtil.TracingContext.empty();
        
        assertNotNull(context);
        assertFalse(context.isValid());
        assertNull(context.getTraceId());
        assertNull(context.getSpanId());
    }
}