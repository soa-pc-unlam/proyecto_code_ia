#!/bin/bash

# Script to initialize the database for the ticket system
# Usage: ./init_db.sh

set -e

echo "🗄️  Inicializando base de datos..."

# Database configuration (can be overridden with environment variables)
DB_HOST=${DB_HOST:-localhost}
DB_PORT=${DB_PORT:-5432}
DB_NAME=${DB_NAME:-ticketdb}
DB_USER=${DB_USER:-ticketuser}
DB_PASSWORD=${DB_PASSWORD:-ticketpass}
DB_ADMIN_USER=${DB_ADMIN_USER:-postgres}

# Check if PostgreSQL is running
if ! command -v psql &> /dev/null; then
    echo "❌ PostgreSQL client not found. Please install psql."
    exit 1
fi

# Create database and user if they don't exist
echo "📌 Creando usuario y base de datos..."
PGPASSWORD=$DB_PASSWORD psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_ADMIN_USER" -tc \
    "SELECT 1 FROM pg_database WHERE datname = '$DB_NAME'" | grep -q 1 || \
    PGPASSWORD=$DB_PASSWORD psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_ADMIN_USER" << EOF
CREATE USER $DB_USER WITH PASSWORD '$DB_PASSWORD';
CREATE DATABASE $DB_NAME OWNER $DB_USER;
ALTER USER $DB_USER CREATEDB;
EOF

# Run schema
echo "📋 Ejecutando schema..."
PGPASSWORD=$DB_PASSWORD psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
    -f db/schema.sql

# Run seed
echo "🌱 Cargando datos de prueba..."
PGPASSWORD=$DB_PASSWORD psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
    -f db/seed.sql

echo "✅ Base de datos inicializada correctamente."
