package com.aioutlet.orderprocessor.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.semconv.ResourceAttributes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenTelemetry configuration for distributed tracing
 */
@Configuration
@ConditionalOnProperty(value = "tracing.enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
public class TracingConfig {

    @Value("${spring.application.name:order-processor-service}")
    private String serviceName;

    @Value("${application.version:1.0.0}")
    private String serviceVersion;

    @Value("${tracing.otlp.endpoint:http://localhost:4317}")
    private String otlpEndpoint;

    @Value("${tracing.enabled:true}")
    private boolean tracingEnabled;

    /**
     * Configure OpenTelemetry SDK
     */
    @Bean
    public OpenTelemetry openTelemetry() {
        if (!tracingEnabled) {
            log.info("Tracing is disabled");
            return OpenTelemetry.noop();
        }

        log.info("Initializing OpenTelemetry tracing for service: {} version: {}", serviceName, serviceVersion);

        try {
            Resource resource = Resource.getDefault()
                    .merge(Resource.builder()
                            .put(ResourceAttributes.SERVICE_NAME, serviceName)
                            .put(ResourceAttributes.SERVICE_VERSION, serviceVersion)
                            .put(ResourceAttributes.SERVICE_INSTANCE_ID, System.getProperty("service.instance.id", "unknown"))
                            .build());

            SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                    .addSpanProcessor(
                            io.opentelemetry.sdk.trace.export.BatchSpanProcessor.builder(
                                    OtlpGrpcSpanExporter.builder()
                                            .setEndpoint(otlpEndpoint)
                                            .build())
                                    .build())
                    .setResource(resource)
                    .build();

            OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                    .setTracerProvider(tracerProvider)
                    .build();

            log.info("✅ OpenTelemetry tracing initialized successfully with endpoint: {}", otlpEndpoint);
            return openTelemetry;

        } catch (Exception e) {
            log.warn("⚠️ Failed to initialize OpenTelemetry: {}", e.getMessage());
            return OpenTelemetry.noop();
        }
    }
}