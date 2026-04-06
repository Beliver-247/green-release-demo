#!/bin/bash

# Build script for Green Release Demo
# This script performs a clean build of all modules

echo "========================================"
echo "Green Release Demo - Build Script"
echo "========================================"
echo ""

echo "Building all modules..."
mvn clean package

if [ $? -eq 0 ]; then
    echo ""
    echo "========================================"
    echo "Build completed successfully!"
    echo "========================================"
    echo ""
    echo "Generated artifacts:"
    ls -lh */target/*.jar 2>/dev/null | grep -v ".original" | awk '{print $9, "(" $5 ")"}'
    echo ""
    echo "To run the application:"
    echo "  java -jar app/target/green-release-app-1.0.0.jar"
    echo "or use:"
    echo "  ./run.sh"
else
    echo ""
    echo "Build failed!"
    exit 1
fi
