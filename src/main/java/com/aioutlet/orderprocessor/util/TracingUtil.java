package com.aioutlet.orderprocessor.util;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Utility class for distributed tracing operations
 * Provides helper methods for getting trace and span IDs, creating spans
 */
@Component
@Slf4j
public class TracingUtil {

    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;
    private final String serviceName;
    private final String serviceVersion;
    private final boolean tracingEnabled;

    @Autowired
    public TracingUtil(@Autowired(required = false) OpenTelemetry openTelemetry,
                       @Value("${spring.application.name:order-processor-service}") String serviceName,
                       @Value("${application.version:1.0.0}") String serviceVersion,
                       @Value("${tracing.enabled:false}") boolean tracingEnabled) {
        this.openTelemetry = openTelemetry != null ? openTelemetry : OpenTelemetry.noop();
        this.serviceName = serviceName;
        this.serviceVersion = serviceVersion;
        this.tracingEnabled = tracingEnabled && openTelemetry != null;
        this.tracer = this.openTelemetry.getTracer(serviceName, serviceVersion);
    }

    /**
     * Get current trace and span IDs from OpenTelemetry context
     * @return TracingContext containing traceId and spanId
     */
    public TracingContext getTracingContext() {
        if (!tracingEnabled) {
            return TracingContext.empty();
        }

        try {
            Span currentSpan = Span.current();
            if (currentSpan != null) {
                SpanContext spanContext = currentSpan.getSpanContext();
                if (spanContext.isValid()) {
                    return new TracingContext(
                            spanContext.getTraceId(),
                            spanContext.getSpanId()
                    );
                }
            }
        } catch (Exception e) {
            log.debug("Failed to get tracing context: {}", e.getMessage());
        }

        return TracingContext.empty();
    }

    /**
     * Create a new span for operation tracking
     * @param operationName Name of the operation
     * @param attributes Additional attributes for the span
     * @return OperationSpan object
     */
    public OperationSpan createOperationSpan(String operationName, Map<String, String> attributes) {
        if (!tracingEnabled) {
            return OperationSpan.noOp();
        }

        try {
            Span span = tracer.spanBuilder(operationName)
                    .setAttribute("service.name", serviceName)
                    .setAttribute("service.version", serviceVersion)
                    .startSpan();

            if (attributes != null) {
                attributes.forEach(span::setAttribute);
            }

            return new OperationSpan(span);
        } catch (Exception e) {
            log.debug("Failed to create operation span: {}", e.getMessage());
            return OperationSpan.noOp();
        }
    }

    /**
     * Create a new span for operation tracking without attributes
     * @param operationName Name of the operation
     * @return OperationSpan object
     */
    public OperationSpan createOperationSpan(String operationName) {
        return createOperationSpan(operationName, null);
    }

    /**
     * Check if tracing is enabled
     * @return true if tracing is enabled
     */
    public boolean isTracingEnabled() {
        return tracingEnabled;
    }

    /**
     * Get service information
     * @return ServiceInfo containing service name and version
     */
    public ServiceInfo getServiceInfo() {
        return new ServiceInfo(serviceName, serviceVersion);
    }

    /**
     * Container for tracing context information
     */
    public static class TracingContext {
        private final String traceId;
        private final String spanId;

        public TracingContext(String traceId, String spanId) {
            this.traceId = traceId;
            this.spanId = spanId;
        }

        public String getTraceId() {
            return traceId;
        }

        public String getSpanId() {
            return spanId;
        }

        public boolean isValid() {
            return traceId != null && spanId != null;
        }

        public static TracingContext empty() {
            return new TracingContext(null, null);
        }

        @Override
        public String toString() {
            return String.format("TracingContext{traceId='%s', spanId='%s'}", traceId, spanId);
        }
    }

    /**
     * Container for operation span
     */
    public static class OperationSpan {
        private final Span span;
        private final String traceId;
        private final String spanId;

        public OperationSpan(Span span) {
            this.span = span;
            SpanContext context = span.getSpanContext();
            this.traceId = context.getTraceId();
            this.spanId = context.getSpanId();
        }

        private OperationSpan() {
            this.span = null;
            this.traceId = null;
            this.spanId = null;
        }

        public String getTraceId() {
            return traceId;
        }

        public String getSpanId() {
            return spanId;
        }

        public void end() {
            if (span != null) {
                span.end();
            }
        }

        public void setStatus(io.opentelemetry.api.trace.StatusCode code, String description) {
            if (span != null) {
                span.setStatus(code, description);
            }
        }

        public void addEvent(String name) {
            if (span != null) {
                span.addEvent(name);
            }
        }

        public void setAttribute(String key, String value) {
            if (span != null) {
                span.setAttribute(key, value);
            }
        }

        public static OperationSpan noOp() {
            return new OperationSpan();
        }
    }

    /**
     * Container for service information
     */
    public static class ServiceInfo {
        private final String serviceName;
        private final String serviceVersion;

        public ServiceInfo(String serviceName, String serviceVersion) {
            this.serviceName = serviceName;
            this.serviceVersion = serviceVersion;
        }

        public String getServiceName() {
            return serviceName;
        }

        public String getServiceVersion() {
            return serviceVersion;
        }
    }
}