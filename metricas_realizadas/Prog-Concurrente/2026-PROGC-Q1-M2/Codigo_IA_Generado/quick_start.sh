#!/bin/bash

# Quick start script for the Ticket System
# This script verifies prerequisites and starts the system

set -e

echo "=================================================="
echo "🎤 Sistema de Venta de Entradas - Quick Start"
echo "=================================================="
echo ""

# Check Python
echo "✅ Verificando Python..."
if ! command -v python3 &> /dev/null; then
    echo "❌ Python 3 no encontrado. Instálalo antes de continuar."
    exit 1
fi
PYTHON_VERSION=$(python3 --version | cut -d' ' -f2)
echo "   Python $PYTHON_VERSION"

# Check PostgreSQL
echo "✅ Verificando PostgreSQL..."
if ! command -v psql &> /dev/null; then
    echo "⚠️  psql no encontrado. Verifica que PostgreSQL esté instalado."
    echo "   En macOS: brew install postgresql@18"
    echo "   En Linux: sudo apt-get install postgresql-18"
    exit 1
fi
PG_VERSION=$(psql --version | cut -d' ' -f3)
echo "   PostgreSQL $PG_VERSION"

# Check if PostgreSQL is running
echo "✅ Verificando servicio PostgreSQL..."
if ! pg_isready -h localhost &> /dev/null; then
    echo "⚠️  PostgreSQL no está ejecutándose."
    echo "   En macOS: brew services start postgresql@18"
    echo "   En Linux: sudo systemctl start postgresql"
    exit 1
fi
echo "   PostgreSQL está ejecutándose"

# Install dependencies
echo "✅ Instalando dependencias Python..."
pip install -q -r requirements.txt
echo "   Dependencias instaladas"

# Initialize database
echo "✅ Inicializando base de datos..."
./init_db.sh > /dev/null 2>&1
echo "   Base de datos inicializada"

echo ""
echo "=================================================="
echo "✨ Sistema listo para ejecutar"
echo "=================================================="
echo ""
echo "Para iniciar el servidor, ejecuta:"
echo "  ./run_server.sh"
echo ""
echo "Luego accede a:"
echo "  🌐 http://localhost:8000"
echo "  📚 API Docs: http://localhost:8000/docs"
echo ""
echo "Credenciales de prueba:"
echo "  Usuario: test_user"
echo "  Contraseña: test123"
echo ""
echo "Para las pruebas de concurrencia:"
echo "  python3 tests/concurrent_test.py"
echo ""
