package com.aioutlet.orderprocessor.filter;

import com.aioutlet.orderprocessor.util.CorrelationIdUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting filter for Order Processor Service
 * Implements token bucket algorithm using Bucket4j library
 */
@Component
@Order(2) // After CorrelationIdFilter
public class RateLimitFilter implements Filter {
    
    private static final Logger logger = LoggerFactory.getLogger(RateLimitFilter.class);
    
    private final Map<String, Bucket> bucketCache = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Value("${app.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;
    
    @Value("${app.rate-limit.requests-per-minute:100}")
    private int requestsPerMinute;
    
    @Value("${app.rate-limit.window-size-minutes:1}")
    private int windowSizeMinutes;
    
    @Value("${app.rate-limit.saga-operations-per-minute:50}")
    private int sagaOperationsPerMinute;
    
    @Value("${app.rate-limit.admin-operations-per-minute:20}")
    private int adminOperationsPerMinute;
    
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        logger.info("Rate limiting filter initialized. Enabled: {}, RequestsPerMinute: {}", 
                   rateLimitEnabled, requestsPerMinute);
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
        
        if (!rateLimitEnabled) {
            chain.doFilter(request, response);
            return;
        }
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        // Skip rate limiting for health checks and metrics
        String path = httpRequest.getRequestURI().toLowerCase();
        if (shouldSkipRateLimiting(path)) {
            chain.doFilter(request, response);
            return;
        }
        
        try {
            String clientKey = getClientKey(httpRequest);
            Bucket bucket = getBucket(clientKey, httpRequest);
            
            if (bucket.tryConsume(1)) {
                // Request allowed
                addRateLimitHeaders(httpResponse, bucket);
                chain.doFilter(request, response);
            } else {
                // Rate limit exceeded
                handleRateLimitExceeded(httpRequest, httpResponse, clientKey);
            }
        } catch (Exception e) {
            logger.error("Error in rate limiting filter", e);
            // Continue processing if rate limiting fails
            chain.doFilter(request, response);
        }
    }
    
    @Override
    public void destroy() {
        bucketCache.clear();
        logger.info("Rate limiting filter destroyed");
    }
    
    /**
     * Check if request should skip rate limiting
     */
    private boolean shouldSkipRateLimiting(String path) {
        return path.startsWith("/actuator/health") || 
               path.startsWith("/actuator/metrics") ||
               path.startsWith("/health") ||
               path.startsWith("/metrics");
    }
    
    /**
     * Generate client key for rate limiting
     */
    private String getClientKey(HttpServletRequest request) {
        // Try to get user identifier from JWT or session
        String userId = extractUserId(request);
        if (userId != null) {
            return "user:" + userId;
        }
        
        // Fallback to IP address
        String clientIp = getClientIpAddress(request);
        return "ip:" + clientIp;
    }
    
    /**
     * Extract user ID from request (JWT token, session, etc.)
     */
    private String extractUserId(HttpServletRequest request) {
        // Try to extract from Authorization header (JWT)
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                // In a real implementation, you would decode the JWT token
                // For now, we'll use a simple approach
                // return JwtUtil.extractUserId(authHeader.substring(7));
                return null; // Placeholder
            } catch (Exception e) {
                logger.debug("Could not extract user ID from JWT", e);
            }
        }
        
        return null;
    }
    
    /**
     * Get client IP address with proxy support
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * Get or create rate limiting bucket for client
     */
    private Bucket getBucket(String clientKey, HttpServletRequest request) {
        return bucketCache.computeIfAbsent(clientKey, key -> createBucket(request));
    }
    
    /**
     * Create rate limiting bucket based on request type
     */
    private Bucket createBucket(HttpServletRequest request) {
        String path = request.getRequestURI().toLowerCase();
        
        // Different limits for different operation types
        if (isSagaOperation(path)) {
            return createBucketWithLimit(sagaOperationsPerMinute);
        } else if (isAdminOperation(path)) {
            return createBucketWithLimit(adminOperationsPerMinute);
        } else {
            return createBucketWithLimit(requestsPerMinute);
        }
    }
    
    /**
     * Create bucket with specified limit
     */
    private Bucket createBucketWithLimit(int requestsPerMinute) {
        Bandwidth limit = Bandwidth.classic(requestsPerMinute, 
                                           Refill.intervally(requestsPerMinute, 
                                                           Duration.ofMinutes(windowSizeMinutes)));
        return Bucket4j.builder()
                .addLimit(limit)
                .build();
    }
    
    /**
     * Check if request is for saga operations
     */
    private boolean isSagaOperation(String path) {
        return path.contains("/saga") || 
               path.contains("/orders") ||
               path.contains("/events");
    }
    
    /**
     * Check if request is for admin operations
     */
    private boolean isAdminOperation(String path) {
        return path.contains("/admin") || 
               path.contains("/management");
    }
    
    /**
     * Add rate limit information to response headers
     */
    private void addRateLimitHeaders(HttpServletResponse response, Bucket bucket) {
        long availableTokens = bucket.getAvailableTokens();
        response.addHeader("X-Rate-Limit-Remaining", String.valueOf(availableTokens));
        response.addHeader("X-Rate-Limit-Limit", String.valueOf(requestsPerMinute));
    }
    
    /**
     * Handle rate limit exceeded scenario
     */
    private void handleRateLimitExceeded(HttpServletRequest request, 
                                       HttpServletResponse response, 
                                       String clientKey) throws IOException {
        
        String correlationId = CorrelationIdUtil.getCorrelationId(request);
        String clientIp = getClientIpAddress(request);
        
        // Log security event
        logger.warn("SECURITY: Rate limit exceeded for client: {}, IP: {}, Path: {}, CorrelationId: {}", 
                   clientKey, clientIp, request.getRequestURI(), correlationId);
        
        // Set response status and headers
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.addHeader("Retry-After", "60");
        response.addHeader("X-Rate-Limit-Exceeded", "true");
        
        // Create error response
        Map<String, Object> errorResponse = Map.of(
            "error", "Rate limit exceeded",
            "message", "Too many requests. Please try again later.",
            "retryAfter", 60,
            "correlationId", correlationId,
            "timestamp", System.currentTimeMillis()
        );
        
        // Write JSON response
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
        response.getWriter().flush();
    }
}
