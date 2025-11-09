package com.aioutlet.orderprocessor.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter for handling correlation IDs in HTTP requests
 * Dapr automatically handles distributed tracing via W3C Trace Context headers
 */
@Component
@Order(1)
public class CorrelationIdFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(CorrelationIdFilter.class);
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String MDC_CORRELATION_ID_KEY = "correlationId";

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
            
            // Add correlation ID to response headers
            httpResponse.setHeader(CORRELATION_ID_HEADER, correlationId);
            
            // Store in request attributes for use in controllers/services
            httpRequest.setAttribute(MDC_CORRELATION_ID_KEY, correlationId);
            
            logger.debug("Processing request {} {} with correlation ID: {}",
                    httpRequest.getMethod(), httpRequest.getRequestURI(), correlationId);
            
            // Continue with the filter chain
            // Note: Dapr automatically handles distributed tracing via W3C Trace Context
            chain.doFilter(request, response);
            
        } finally {
            // Clean up MDC to prevent memory leaks
            MDC.remove(MDC_CORRELATION_ID_KEY);
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
