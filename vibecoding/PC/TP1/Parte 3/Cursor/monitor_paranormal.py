import multiprocessing as mp
import time
import random
import sys
from typing import List, Dict
from dataclasses import dataclass
from enum import Enum

class EventoParanormal(Enum):
    """Enumeración de los eventos paranormales que pueden detectar las cámaras"""
    SIN_ACTIVIDAD = "Sin actividad"
    MOVIMIENTO_DETECTADO = "Movimiento detectado"
    ANOMALIA_TERMICA = "Anomalía térmica"
    SOMBRA_EXTRAÑA = "Sombra extraña"
    RUIDO_DETECTADO = "Ruido detectado"

def generar_evento_aleatorio() -> EventoParanormal:
    """Genera un evento aleatorio con probabilidades predefinidas"""
    # Probabilidades de cada evento (suman 100%)
    probabilidades = {
        EventoParanormal.SIN_ACTIVIDAD: 60,      # 60% - más común
        EventoParanormal.MOVIMIENTO_DETECTADO: 20, # 20%
        EventoParanormal.ANOMALIA_TERMICA: 10,    # 10%
        EventoParanormal.SOMBRA_EXTRAÑA: 7,       # 7%
        EventoParanormal.RUIDO_DETECTADO: 3       # 3% - más raro
    }
    
    # Generar número aleatorio
    numero = random.randint(1, 100)
    acumulado = 0
    
    for evento, prob in probabilidades.items():
        acumulado += prob
        if numero <= acumulado:
            return evento
            
    return EventoParanormal.SIN_ACTIVIDAD  # Por defecto

def es_evento_paranormal(evento: EventoParanormal) -> bool:
    """Determina si un evento es considerado paranormal"""
    return evento != EventoParanormal.SIN_ACTIVIDAD

def monitorear_zona(camara_id: int, zona: str, duracion: int, frecuencia: int, resultados: Dict):
    """Función que ejecuta cada proceso de cámara"""
    print(f"[INICIO] CÁMARA {camara_id} iniciando monitoreo de {zona}")
    
    tiempo_inicio = time.time()
    tiempo_fin = tiempo_inicio + duracion
    eventos_paranormales = 0
    
    while time.time() < tiempo_fin:
        # Generar evento aleatorio
        evento = generar_evento_aleatorio()
        
        # Imprimir reporte
        timestamp = time.strftime("%H:%M:%S")
        print(f"[{timestamp}] CÁMARA {camara_id} | ZONA: {zona} | EVENTO: {evento.value}")
        
        # Contar eventos paranormales
        if es_evento_paranormal(evento):
            eventos_paranormales += 1
        
        # Esperar hasta el próximo reporte
        time.sleep(frecuencia)
    
    # Guardar resultado final
    resultados[camara_id] = eventos_paranormales
    print(f"[FIN] CÁMARA {camara_id} finalizada. Eventos paranormales detectados: {eventos_paranormales}")

class SistemaMonitoreoParanormal:
    """Sistema principal de monitoreo paranormal"""
    
    def __init__(self):
        self.zonas = [
            "Sótano 2",
            "Ático",
            "Cocina", 
            "Dormitorio",
            "Jardín",
            "Mausoleo"
        ]
        self.manager = mp.Manager()
        self.resultados = self.manager.dict()
    
    def iniciar_monitoreo(self, duracion: int, frecuencia: int):
        """Inicia el monitoreo con todas las cámaras"""
        print("=" * 60)
        print("SISTEMA DE MONITOREO PARANORMAL INICIADO")
        print("=" * 60)
        print(f"Duración del monitoreo: {duracion} segundos")
        print(f"Frecuencia de reportes: {frecuencia} segundos")
        print(f"Cámaras activas: {len(self.zonas)}")
        print("=" * 60)
        
        # Crear procesos para cada cámara
        procesos = []
        for i, zona in enumerate(self.zonas, 1):
            proceso = mp.Process(
                target=monitorear_zona,
                args=(i, zona, duracion, frecuencia, self.resultados)
            )
            procesos.append(proceso)
            proceso.start()
        
        # Esperar a que todos los procesos terminen
        for proceso in procesos:
            proceso.join()
        
        # Mostrar resumen final
        self.mostrar_resumen_final()
    
    def mostrar_resumen_final(self):
        """Muestra el resumen final de todos los eventos detectados"""
        print("\n" + "=" * 60)
        print("RESUMEN FINAL DEL MONITOREO")
        print("=" * 60)
        
        total_eventos = 0
        for camara_id in range(1, len(self.zonas) + 1):
            eventos = self.resultados.get(camara_id, 0)
            zona = self.zonas[camara_id - 1]
            print(f"CÁMARA {camara_id} ({zona}): {eventos} eventos paranormales")
            total_eventos += eventos
        
        print("-" * 60)
        print(f"TOTAL DE EVENTOS PARANORMALES: {total_eventos}")
        
        if total_eventos > 0:
            print("¡ACTIVIDAD PARANORMAL DETECTADA! ¡Los investigadores deben investigar!")
        else:
            print("No se detectó actividad paranormal. La mansión parece estar tranquila.")
        
        print("=" * 60)

def main():
    """Función principal del programa"""
    # Verificar argumentos de línea de comandos
    if len(sys.argv) != 3:
        print("Uso: python monitor_paranormal.py <duracion_segundos> <frecuencia_segundos>")
        print("Ejemplo: python monitor_paranormal.py 30 5")
        sys.exit(1)
    
    try:
        duracion = int(sys.argv[1])
        frecuencia = int(sys.argv[2])
        
        if duracion <= 0 or frecuencia <= 0:
            raise ValueError("Los valores deben ser positivos")
            
    except ValueError as e:
        print(f"Error: {e}")
        print("Los parámetros deben ser números enteros positivos")
        sys.exit(1)
    
    # Crear y ejecutar el sistema
    sistema = SistemaMonitoreoParanormal()
    sistema.iniciar_monitoreo(duracion, frecuencia)

if __name__ == "__main__":
    main() 