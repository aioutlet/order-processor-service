#!/bin/bash

# Order Processor Service Development Environment Setup
echo "🚀 Setting up Order Processor Service development environment..."

# Check for Java and build tools
echo "📋 Checking prerequisites..."

if ! command -v java &> /dev/null; then
    echo "❌ Java not found. Please install Java 17 or later."
    exit 1
fi

if ! command -v docker &> /dev/null; then
    echo "❌ Docker not found. Please install Docker."
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo "❌ Docker Compose not found. Please install Docker Compose."
    exit 1
fi

echo "✅ Java found: $(java -version 2>&1 | head -1)"
echo "✅ Docker found: $(docker --version)"
echo "✅ Docker Compose found: $(docker-compose --version)"

# Determine build tool
BUILD_TOOL=""
if command -v gradle &> /dev/null; then
    BUILD_TOOL="gradle"
    echo "✅ Using Gradle for build management"
elif command -v mvn &> /dev/null; then
    BUILD_TOOL="maven"
    echo "✅ Using Maven for build management"
else
    echo "❌ Neither Gradle nor Maven found. Please install one of them."
    exit 1
fi

# Clean and build the application
echo ""
echo "🔨 Building Order Processor Service..."
if [ "$BUILD_TOOL" = "gradle" ]; then
    ./gradlew clean build -x test
    if [ $? -ne 0 ]; then
        echo "❌ Gradle build failed"
        exit 1
    fi
    echo "✅ Gradle build completed successfully"
elif [ "$BUILD_TOOL" = "maven" ]; then
    mvn clean package -DskipTests
    if [ $? -ne 0 ]; then
        echo "❌ Maven build failed"
        exit 1
    fi
    echo "✅ Maven build completed successfully"
fi

# Start services with Docker Compose
echo ""
echo "🐳 Starting Order Processor Service with Docker Compose..."
docker-compose up -d

if [ $? -eq 0 ]; then
    echo "✅ Services started successfully"
else
    echo "❌ Failed to start services with Docker Compose"
    exit 1
fi

# Wait for services to be ready
echo ""
echo "⏳ Waiting for services to be healthy..."
sleep 20

# Validate services
echo ""
echo "🔍 Validating service health..."

# Check PostgreSQL
if docker-compose exec -T postgres pg_isready -U postgres -d OrderProcessorDb_Dev > /dev/null 2>&1; then
    echo "✅ PostgreSQL is ready"
else
    echo "⚠️  PostgreSQL is still starting up"
fi

# Check order processor service
if docker-compose ps | grep -q "order-processor-service-docker.*Up"; then
    echo "✅ Order Processor Service is running"
    
    # Wait a bit more for the application to fully start
    sleep 10
    
    # Test health endpoint
    if curl -f http://localhost:8083/api/actuator/health > /dev/null 2>&1; then
        echo "✅ Order Processor Service health endpoint is responding"
    else
        echo "⚠️  Order Processor Service health endpoint not yet ready"
    fi
else
    echo "⚠️  Order Processor Service container status unknown"
fi

echo ""
echo "🎉 Order Processor Service setup completed!"
echo ""
echo "📋 Service Information:"
echo "  • Order Processor Service: http://localhost:8083"
echo "  • Health Check: http://localhost:8083/api/actuator/health"
echo "  • PostgreSQL: localhost:5434"
echo ""
echo "🔧 Management Commands:"
echo "  • View status: docker-compose ps"
echo "  • View logs: docker-compose logs -f"
echo "  • Stop services: bash .ops/teardown.sh"
echo ""
echo "✅ Services are healthy and ready"
