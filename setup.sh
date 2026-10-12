#!/bin/bash

# paraspa Setup Script
# This script helps set up the development environment

set -e

echo "========================================"
echo "  paraspa - Setup Script"
echo "========================================"
echo ""

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "❌ Maven is not installed"
    echo "Please install Maven from https://maven.apache.org/"
    exit 1
fi
echo "✓ Maven found: $(mvn --version | head -n 1)"

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "❌ Java is not installed"
    echo "Please install Java 17 or higher"
    exit 1
fi
echo "✓ Java found: $(java -version 2>&1 | head -n 1)"

# Check if PostgreSQL is installed
if ! command -v psql &> /dev/null; then
    echo "⚠️  PostgreSQL command-line tool (psql) not found"
    echo "Make sure PostgreSQL is installed and running"
else
    echo "✓ PostgreSQL found"
fi

echo ""
echo "========================================"
echo "  Creating Database"
echo "========================================"
echo ""

# Attempt to create database
if command -v psql &> /dev/null; then
    echo "Attempting to create database 'paraspa_db'..."
    psql -U postgres -h localhost -c "CREATE DATABASE paraspa_db;" 2>/dev/null || {
        echo "⚠️  Could not auto-create database"
        echo "You may need to create it manually:"
        echo "  psql -U postgres -h localhost -c \"CREATE DATABASE paraspa_db;\""
    }
fi

echo ""
echo "========================================"
echo "  Building Project"
echo "========================================"
echo ""

cd "$(dirname "$0")"

echo "Running: mvn clean install -DskipTests"
mvn clean install -DskipTests

echo ""
echo "========================================"
echo "  Setup Complete!"
echo "========================================"
echo ""
echo "Next steps:"
echo ""
echo "1. Make sure PostgreSQL is running:"
echo "   - Default: localhost:5432"
echo "   - Username: postgres"
echo "   - Password: postgres"
echo ""
echo "2. Run the application:"
echo "   mvn spring-boot:run"
echo ""
echo "3. The API will be available at:"
echo "   http://localhost:8080"
echo ""
echo "4. Test the API with:"
echo "   bash API-TESTING.md"
echo ""
echo "5. Check the README.md for more information"
echo ""
echo "Database will be auto-created on first run with:"
echo "   spring.jpa.hibernate.ddl-auto=update"
echo ""

