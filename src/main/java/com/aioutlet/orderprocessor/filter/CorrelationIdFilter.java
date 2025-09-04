package com.aioutlet.orderprocessor.filter;

import com.aioutlet.orderprocessor.util.TracingUtil;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter for handling correlation IDs and tracing context in HTTP requests
 * This filter ensures every request has a correlation ID and trace context for distributed tracing
 */
@Component
@Order(1) // Ensure this filter runs first
public class CorrelationIdFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(CorrelationIdFilter.class);
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String MDC_CORRELATION_ID_KEY = "correlationId";
    private static final String MDC_TRACE_ID_KEY = "traceId";
    private static final String MDC_SPAN_ID_KEY = "spanId";
    private static final String MDC_SERVICE_NAME_KEY = "serviceName";
    private static final String MDC_SERVICE_VERSION_KEY = "serviceVersion";

    @Autowired(required = false)
    private TracingUtil tracingUtil;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        logger.info("CorrelationIdFilter initialized");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        try {
            // Extract correlation ID from header or generate new one
            String correlationId = extractOrGenerateCorrelationId(httpRequest);
            
            // Set correlation ID in MDC for logging
            MDC.put(MDC_CORRELATION_ID_KEY, correlationId);
            
            // Get and set tracing context in MDC
            if (tracingUtil != null) {
                TracingUtil.TracingContext tracingContext = tracingUtil.getTracingContext();
                if (tracingContext.isValid()) {
                    MDC.put(MDC_TRACE_ID_KEY, tracingContext.getTraceId());
                    MDC.put(MDC_SPAN_ID_KEY, tracingContext.getSpanId());
                }
                
                // Add service information to MDC
                TracingUtil.ServiceInfo serviceInfo = tracingUtil.getServiceInfo();
                MDC.put(MDC_SERVICE_NAME_KEY, serviceInfo.getServiceName());
                MDC.put(MDC_SERVICE_VERSION_KEY, serviceInfo.getServiceVersion());
            }
            
            // Add correlation ID to response headers
            httpResponse.setHeader(CORRELATION_ID_HEADER, correlationId);
            
            // Store in request attributes for use in controllers/services
            httpRequest.setAttribute(MDC_CORRELATION_ID_KEY, correlationId);
            
            logger.debug("Processing request {} {} with correlation ID: {}",
                    httpRequest.getMethod(), httpRequest.getRequestURI(), correlationId);
            
            // Continue with the filter chain
            chain.doFilter(request, response);
            
        } finally {
            // Clean up MDC to prevent memory leaks
            MDC.remove(MDC_CORRELATION_ID_KEY);
            MDC.remove(MDC_TRACE_ID_KEY);
            MDC.remove(MDC_SPAN_ID_KEY);
            MDC.remove(MDC_SERVICE_NAME_KEY);
            MDC.remove(MDC_SERVICE_VERSION_KEY);
        }
    }

    @Override
    public void destroy() {
        logger.info("CorrelationIdFilter destroyed");
    }

    /**
     * Extract correlation ID from request header or generate a new one
     */
    private String extractOrGenerateCorrelationId(HttpServletRequest request) {
        // Try to get from standard header
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        
        if (correlationId == null || correlationId.trim().isEmpty()) {
            // Try lowercase version (some clients might use this)
            correlationId = request.getHeader("x-correlation-id");
        }
        
        if (correlationId == null || correlationId.trim().isEmpty()) {
            // Generate new correlation ID
            correlationId = UUID.randomUUID().toString();
        }
        
        return correlationId;
    }
}
