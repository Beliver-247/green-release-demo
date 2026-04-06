#!/bin/bash

# Run script for Green Release Demo
# This script starts the Spring Boot application

echo "========================================"
echo "Green Release Demo - Starting Application"
echo "========================================"
echo ""

JAR_FILE="app/target/green-release-app-1.0.0.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "Error: JAR file not found: $JAR_FILE"
    echo "Please run ./build.sh first"
    exit 1
fi

echo "Starting application on http://localhost:8080"
echo ""
echo "Available endpoints:"
echo "  - GET http://localhost:8080/health"
echo "  - GET http://localhost:8080/users"
echo "  - GET http://localhost:8080/orders"
echo ""
echo "Press Ctrl+C to stop the application"
echo ""

java -jar "$JAR_FILE"
