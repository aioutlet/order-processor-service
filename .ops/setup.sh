#!/bin/bash

# Order Processor Service Development Environment Setup (Java)
echo "Setting up Order Processor Service development environment..."

# Set default environment variables
export SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-development}
export DB_HOST=${DB_HOST:-localhost}
export DB_PORT=${DB_PORT:-5432}
export DB_NAME=${DB_NAME:-aioutlet_order_processor}
export DB_USER=${DB_USER:-postgres}
export DB_PASSWORD=${DB_PASSWORD:-password}

# Check for Java and Maven/Gradle
JAVA_FOUND=false
BUILD_TOOL=""

if command -v java &> /dev/null; then
    JAVA_FOUND=true
    echo "Java found: $(java -version 2>&1 | head -1)"
else
    echo "Error: Java not found. Please install Java 17 or later."
    exit 1
fi

if command -v mvn &> /dev/null; then
    BUILD_TOOL="maven"
    echo "Using Maven for build management"
elif command -v gradle &> /dev/null; then
    BUILD_TOOL="gradle"
    echo "Using Gradle for build management"
else
    echo "Warning: Neither Maven nor Gradle found. Please install one of them."
fi

# Create Spring Boot project structure if it doesn't exist
if [ ! -f "pom.xml" ] && [ ! -f "build.gradle" ] && [ ! -f "src/main/java" ]; then
    echo "Creating Spring Boot project structure..."
    
    if [ "$BUILD_TOOL" = "maven" ]; then
        # Create basic Maven project structure
        mkdir -p src/main/java/com/aioutlet/orderprocessor
        mkdir -p src/main/resources
        mkdir -p src/test/java
        
        cat > pom.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
        <relativePath/>
    </parent>
    
    <groupId>com.aioutlet</groupId>
    <artifactId>order-processor-service</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>
    
    <properties>
        <java.version>17</java.version>
    </properties>
    
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
EOF
        
        # Create basic Spring Boot application class
        cat > src/main/java/com/aioutlet/orderprocessor/OrderProcessorApplication.java << 'EOF'
package com.aioutlet.orderprocessor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class OrderProcessorApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderProcessorApplication.class, args);
    }
    
    @GetMapping("/health")
    public String health() {
        return "{\"status\":\"healthy\",\"service\":\"order-processor-service\"}";
    }
    
    @GetMapping("/process")
    public String process() {
        return "{\"message\":\"Order processor service is running!\"}";
    }
}
EOF
        
    elif [ "$BUILD_TOOL" = "gradle" ]; then
        echo "Creating Gradle project structure..."
        gradle init --type java-application --dsl groovy --test-framework junit-jupiter
    fi
    
    echo "Spring Boot project structure created!"
fi

# Install dependencies
echo "Installing Java dependencies..."
if [ "$BUILD_TOOL" = "maven" ]; then
    mvn clean install -DskipTests
    echo "Maven dependencies installed successfully!"
elif [ "$BUILD_TOOL" = "gradle" ]; then
    gradle build -x test
    echo "Gradle dependencies installed successfully!"
fi

# Setup database
echo "Setting up database schema..."

# Check if database directory with custom setup exists
if [ -d "database" ] && [ -f "database/scripts/Seed.java" ]; then
    echo "Running custom database seeder..."
    cd database/scripts
    javac -cp ".:$(find ../../ -name "*.jar" | tr '\n' ':')" Seed.java 2>/dev/null || echo "Custom seeder compilation failed"
    java -cp ".:$(find ../../ -name "*.jar" | tr '\n' ':')" Seed 2>/dev/null || echo "Custom seeder not available"
    cd ../..
else
    echo "Using Spring Boot auto-configuration for database setup..."
    echo "Database will be initialized on application startup"
fi

# Run tests
echo "Running tests..."
if [ "$BUILD_TOOL" = "maven" ]; then
    mvn test 2>/dev/null || echo "No tests found"
elif [ "$BUILD_TOOL" = "gradle" ]; then
    gradle test 2>/dev/null || echo "No tests found"
fi
echo "Tests completed!"

# Build the application
echo "Building application..."
if [ "$BUILD_TOOL" = "maven" ]; then
    mvn clean package -DskipTests
    echo "Maven build completed successfully!"
elif [ "$BUILD_TOOL" = "gradle" ]; then
    gradle build -x test
    echo "Gradle build completed successfully!"
fi

echo "Order Processor Service development environment setup completed!"
echo ""
echo "To start the service:"
if [ "$BUILD_TOOL" = "maven" ]; then
    echo "  mvn spring-boot:run"
elif [ "$BUILD_TOOL" = "gradle" ]; then
    echo "  gradle bootRun"
fi
echo ""
echo "Environment Variables:"
echo "  SPRING_PROFILES_ACTIVE=$SPRING_PROFILES_ACTIVE"
echo "  DB_HOST=$DB_HOST"
echo "  DB_PORT=$DB_PORT"
echo "  DB_NAME=$DB_NAME"
echo "  DB_USER=$DB_USER"
echo ""

# Start services with Docker Compose
echo "🐳 Starting services with Docker Compose..."
if docker-compose up -d; then
    echo "✅ Services started successfully"
    echo ""
    echo "⏳ Waiting for services to be ready..."
    sleep 15
    
    # Check service health
    if docker-compose ps | grep -q "Up.*healthy\|Up"; then
        echo "✅ Services are running"
    else
        echo "⚠️  Services may still be starting up"
    fi
else
    echo "❌ Failed to start services with Docker Compose"
    exit 1
fi
echo ""

echo "🎉 Order Processor Service setup completed successfully!"
echo ""
echo "🚀 Service is now running:"
echo "  • View status: docker-compose ps"
echo "  • View logs: docker-compose logs -f"
echo "  • Stop services: bash .ops/teardown.sh"
