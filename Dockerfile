# =============================================================================
# Optimized Multi-stage Dockerfile for Java Order Processor Service
# =============================================================================

# -----------------------------------------------------------------------------
# Maven Cache stage - Download dependencies only when pom.xml changes
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS maven-cache
WORKDIR /app

# Install Maven (use built-in Maven for faster builds)
RUN apk add --no-cache maven

# Copy only pom.xml for better caching
COPY pom.xml ./

# Create a dummy source directory to prevent Maven warnings
RUN mkdir -p src/main/java

# Download all dependencies and plugins (this layer will be cached)
RUN mvn dependency:go-offline dependency:resolve-sources -B --no-transfer-progress

# -----------------------------------------------------------------------------
# Build stage - Build the application
# -----------------------------------------------------------------------------
FROM maven-cache AS build

# Copy source code
COPY src/ src/

# Build application with optimized settings
RUN mvn clean package -DskipTests=true -B --no-transfer-progress \
    -Dmaven.javadoc.skip=true \
    -Dmaven.source.skip=true

# -----------------------------------------------------------------------------
# Runtime base - Optimized JRE image
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime-base

# Install only essential system dependencies
RUN apk add --no-cache \
    curl \
    dumb-init && \
    rm -rf /var/cache/apk/*

# Create non-root user and app directory
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

WORKDIR /app
RUN chown appuser:appgroup /app

# -----------------------------------------------------------------------------
# Production stage - Final optimized image
# -----------------------------------------------------------------------------
FROM runtime-base AS production

# Copy the JAR file from build stage
COPY --from=build --chown=appuser:appgroup /app/target/*.jar app.jar

# Switch to non-root user
USER appuser

# Health check with optimized settings
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8083/api/actuator/health || exit 1

# Expose port
EXPOSE 8083

# Optimized JVM settings for containerized Spring Boot
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -XX:+UnlockExperimentalVMOptions \
               -XX:+UseJVMCICompiler \
               -Xss256k \
               -XX:ReservedCodeCacheSize=128m \
               -Djava.security.egd=file:/dev/./urandom \
               -Dspring.backgroundpreinitializer.ignore=true"

# Use dumb-init for proper signal handling
ENTRYPOINT ["dumb-init", "java"]
CMD ["-jar", "app.jar"]

# Labels for better image management
LABEL maintainer="AIOutlet Team"
LABEL service="order-processor-service"
LABEL version="1.0.0"
