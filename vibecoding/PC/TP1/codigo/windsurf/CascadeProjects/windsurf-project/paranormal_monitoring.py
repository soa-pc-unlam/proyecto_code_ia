import argparse
import random
import time
import multiprocessing
from dataclasses import dataclass
from typing import List, Dict
from datetime import datetime

# Constants
ZONES = ["Sótano 2", "Ático", "Cocina", "Dormitorio", "Jardín", "Mausoleo"]
EVENT_TYPES = [
    "Sin actividad",
    "Movimiento detectado",
    "Anomalía térmica",
    "Sombra extraña",
    "Ruido detectado"
]
PARANORMAL_EVENTS = ["Movimiento detectado", "Anomalía térmica", "Sombra extraña"]

@dataclass
class CameraEvent:
    camera_id: int
    zone: str
    event: str
    timestamp: str

def camera_process(camera_id: int, zone: str, report_interval: float, duration: float, results_queue):
    """
    Simulates a camera monitoring a specific zone.
    
    Args:
        camera_id: Unique identifier for the camera
        zone: Zone being monitored by this camera
        report_interval: How often to report events (in seconds)
        duration: Total monitoring duration (in seconds)
        results_queue: Queue to store the results
    """
    start_time = time.time()
    paranormal_count = 0
    
    print(f"[Cámara {camera_id}] Iniciando monitoreo de {zone}")
    
    while (time.time() - start_time) < duration:
        # Generate a random event, with higher probability for "Sin actividad"
        if random.random() < 0.7:  # 70% chance of no activity
            event = "Sin actividad"
        else:
            event = random.choice(EVENT_TYPES[1:])  # Random event excluding "Sin actividad"
            
            if event in PARANORMAL_EVENTS:
                paranormal_count += 1
        
        # Create and log the event
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        log_entry = f"[{timestamp}] Cámara {camera_id:02d} | Zona: {zone:10} | Evento: {event}"
        print(log_entry)
        
        # Store the event
        results_queue.put(CameraEvent(camera_id, zone, event, timestamp))
        
        # Wait until next report
        time.sleep(report_interval)
    
    # Return the results
    results_queue.put({
        'camera_id': camera_id,
        'zone': zone,
        'paranormal_count': paranormal_count
    })

def main():
    # Parse command line arguments
    parser = argparse.ArgumentParser(description='Sistema de monitoreo paranormal')
    parser.add_argument('--duracion', type=int, required=True, 
                       help='Duración del monitoreo en segundos')
    parser.add_argument('--frecuencia', type=float, required=True,
                       help='Frecuencia de reporte en segundos')
    args = parser.parse_args()
    
    print(f"""
    ===========================================
    SISTEMA DE MONITOREO PARANORMAL INICIADO
    Duración: {args.duracion} segundos
    Frecuencia de reporte: {args.frecuencia} segundos
    ===========================================
    """)
    
    # Create a queue to collect results from processes
    manager = multiprocessing.Manager()
    results_queue = manager.Queue()
    processes = []
    
    # Start a process for each camera
    for i, zone in enumerate(ZONES, 1):
        p = multiprocessing.Process(
            target=camera_process,
            args=(i, zone, args.frecuencia, args.duracion, results_queue)
        )
        processes.append(p)
        p.start()
    
    # Wait for all processes to complete
    for p in processes:
        p.join()
    
    # Collect and display final results
    print("\n" + "="*50)
    print("RESUMEN FINAL DE ACTIVIDAD PARANORMAL")
    print("="*50)
    
    total_paranormal = 0
    while not results_queue.empty():
        result = results_queue.get()
        if isinstance(result, dict):  # Final summary from a camera
            print(f"Cámara {result['camera_id']:02d} ({result['zone']}): {result['paranormal_count']} eventos paranormales")
            total_paranormal += result['paranormal_count']
    
    print("-"*50)
    print(f"TOTAL DE EVENTOS PARANORMALES DETECTADOS: {total_paranormal}")
    print("="*50)

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\nMonitoreo interrumpido por el usuario.")
    except Exception as e:
        print(f"\nError inesperado: {e}")
    finally:
        print("Sistema de monitoreo finalizado.")
