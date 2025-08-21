#!/bin/bash

# Order Processor Service - Development Environment Teardown Script
echo "🧹 Tearing down Order Processor Service development environment..."

# Check if docker-compose is available
if ! command -v docker-compose &> /dev/null; then
    echo "❌ Docker Compose not found. Please install Docker Compose."
    exit 1
fi

# Stop and remove containers
echo "📦 Stopping and removing containers..."
if docker-compose down; then
    echo "✅ Containers stopped and removed successfully"
else
    echo "⚠️  Some issues stopping containers (they may not be running)"
fi

# Remove volumes (with confirmation for safety)
read -p "🗂️  Remove volumes? This will delete all database data! (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "�️  Removing volumes..."
    docker-compose down -v
    echo "✅ Volumes removed"
else
    echo "⚠️  Volumes preserved"
fi

# Clean up any orphaned containers
echo "🧽 Cleaning up orphaned containers..."
docker-compose down --remove-orphans > /dev/null 2>&1 || true

echo ""
echo "🎉 Order Processor Service teardown completed!"
echo ""
echo "ℹ️  If you want to remove everything including images:"
echo "   docker-compose down --rmi all -v"
