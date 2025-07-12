#!/usr/bin/env python3
"""
Script de prueba para el Sistema de Monitoreo Paranormal
Demuestra el funcionamiento básico del sistema con diferentes configuraciones
"""

import subprocess
import sys
import time

def ejecutar_prueba(duracion, frecuencia, descripcion):
    """Ejecuta una prueba del sistema con los parámetros especificados"""
    print(f"\n{'='*60}")
    print(f"🧪 PRUEBA: {descripcion}")
    print(f"⏱️  Duración: {duracion}s | 📡 Frecuencia: {frecuencia}s")
    print(f"{'='*60}")
    
    try:
        # Ejecutar el sistema
        resultado = subprocess.run([
            sys.executable, 
            "monitor_paranormal.py", 
            str(duracion), 
            str(frecuencia)
        ], capture_output=True, text=True, timeout=duracion + 10)
        
        # Mostrar salida
        print(resultado.stdout)
        
        if resultado.stderr:
            print("ERRORES:")
            print(resultado.stderr)
            
        return resultado.returncode == 0
        
    except subprocess.TimeoutExpired:
        print("❌ La prueba excedió el tiempo límite")
        return False
    except Exception as e:
        print(f"❌ Error ejecutando la prueba: {e}")
        return False

def main():
    """Función principal para ejecutar todas las pruebas"""
    print("👻 SISTEMA DE MONITOREO PARANORMAL - PRUEBAS")
    print("=" * 60)
    
    # Lista de pruebas a ejecutar
    pruebas = [
        (15, 3, "Prueba rápida - 15 segundos, reportes cada 3s"),
        (30, 5, "Prueba estándar - 30 segundos, reportes cada 5s"),
        (45, 7, "Prueba extendida - 45 segundos, reportes cada 7s"),
    ]
    
    exitos = 0
    total_pruebas = len(pruebas)
    
    for duracion, frecuencia, descripcion in pruebas:
        if ejecutar_prueba(duracion, frecuencia, descripcion):
            print("✅ Prueba exitosa")
            exitos += 1
        else:
            print("❌ Prueba fallida")
        
        # Pausa entre pruebas
        time.sleep(2)
    
    # Resumen final
    print(f"\n{'='*60}")
    print("📊 RESUMEN DE PRUEBAS")
    print(f"{'='*60}")
    print(f"✅ Pruebas exitosas: {exitos}/{total_pruebas}")
    print(f"❌ Pruebas fallidas: {total_pruebas - exitos}/{total_pruebas}")
    
    if exitos == total_pruebas:
        print("🎉 ¡Todas las pruebas pasaron exitosamente!")
    else:
        print("⚠️  Algunas pruebas fallaron. Revisar el sistema.")
    
    print(f"{'='*60}")

if __name__ == "__main__":
    main() 