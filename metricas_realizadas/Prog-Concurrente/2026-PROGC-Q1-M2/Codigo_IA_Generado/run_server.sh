#!/bin/bash

# Script to run the ticket system backend server
# Usage: ./run_server.sh

set -e

echo "🚀 Iniciando Sistema de Venta de Entradas..."

# Database configuration (can be set via environment variables)
export DB_HOST=${DB_HOST:-localhost}
export DB_PORT=${DB_PORT:-5432}
export DB_NAME=${DB_NAME:-ticketdb}
export DB_USER=${DB_USER:-ticketuser}
export DB_PASSWORD=${DB_PASSWORD:-ticketpass}

# Server configuration
export HOST=${HOST:-0.0.0.0}
export PORT=${PORT:-8000}

# Check if dependencies are installed
echo "📦 Verificando dependencias..."
if ! python3 -c "import fastapi" 2>/dev/null; then
    echo "❌ Dependencias no instaladas. Ejecute: pip install -r requirements.txt"
    exit 1
fi

# Navigate to backend directory
cd backend

# Start the server
echo "🎵 Servidor ejecutándose en http://$HOST:$PORT"
echo "📚 Documentación disponible en http://$HOST:$PORT/docs"
echo ""

python3 -m uvicorn main:app \
    --host "$HOST" \
    --port "$PORT" \
    --reload \
    --log-level info
