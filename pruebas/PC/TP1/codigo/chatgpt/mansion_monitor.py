# -*- coding: utf-8 -*-
"""
Monitoreo de cámaras paranormales - Árbol de procesos (Windows)
Autor: ChatGPT
Descripción:
- Un proceso por cámara/zona.
- Cada proceso reporta un evento aleatorio cada 'frequency' segundos durante 'duration' segundos.
- Eventos paranormales: todos excepto "Sin actividad".
- El proceso principal espera a todos y muestra un resumen final.

Ejemplo de uso:
  python mansion_monitor.py --duration 30 --frequency 3
"""

import argparse
import multiprocessing as mp
import os
import queue
import random
import signal
import sys
import time
from datetime import datetime

ZONAS = [
    "Sótano 2",
    "Ático",
    "Cocina",
    "Dormitorio",
    "Jardín",
    "Mausoleo",
]

EVENTOS = [
    "Sin actividad",
    "Movimiento detectado",
    "Anomalía térmica",
    "Sombra extraña",
    "Ruido detectado",
]

# Distribución por defecto (puedes ajustarla a gusto)
# Debe sumar 1.0
PROBABILIDADES = {
    "Sin actividad":       0.60,
    "Movimiento detectado":0.15,
    "Anomalía térmica":    0.10,
    "Sombra extraña":      0.10,
    "Ruido detectado":     0.05,
}

PARANORMAL = set(EVENTOS) - {"Sin actividad"}

def elegir_evento(rng: random.Random) -> str:
    # Elegir evento según las probabilidades definidas
    eventos = list(PROBABILIDADES.keys())
    pesos = [PROBABILIDADES[e] for e in eventos]
    # random.choices está bien para nuestra necesidad
    return rng.choices(eventos, weights=pesos, k=1)[0]

def ts() -> str:
    return datetime.now().strftime("%H:%M:%S")

def proceso_camara(camera_id: int, zona: str, duration: float, frequency: float, result_queue: mp.Queue, seed: int):
    """
    Proceso hijo: reporta eventos cada 'frequency' segundos durante 'duration' segundos.
    Al finalizar, envía (camera_id, zona, total_paranormales, total_eventos) por la cola.
    """
    rng = random.Random(seed)
    inicio = time.perf_counter()
    siguiente = inicio
    paranormales = 0
    total_eventos = 0

    # Para terminar si el padre muere en Windows, no hay fácil archivo descriptor; confiamos en la duración
    try:
        while True:
            ahora = time.perf_counter()
            if ahora - inicio >= duration:
                break
            if ahora >= siguiente:
                evento = elegir_evento(rng)
                total_eventos += 1
                if evento in PARANORMAL:
                    paranormales += 1
                print(f"[{ts()}] CAM {camera_id:02d} | {zona:<12} | {evento}")
                # Programar siguiente tick
                siguiente += frequency
            else:
                # Dormir lo justo para no ocupar CPU
                time.sleep(min(0.05, (siguiente - ahora)))
    except KeyboardInterrupt:
        # Permite Ctrl+C limpio
        pass
    finally:
        # Informe final de la cámara
        print(f"[{ts()}] CAM {camera_id:02d} | {zona:<12} | Finalizando. Paranormales: {paranormales} / {total_eventos}")
        try:
            result_queue.put((camera_id, zona, paranormales, total_eventos), timeout=2.0)
        except Exception:
            pass

def parse_args():
    p = argparse.ArgumentParser(description="Monitoreo de cámaras (procesos múltiples)")
    p.add_argument("--duration", "-d", type=float, required=True,
                   help="Duración del monitoreo en segundos (p.ej., 30)")
    p.add_argument("--frequency", "-f", type=float, required=True,
                   help="Frecuencia de reporte por cámara en segundos (p.ej., 3)")
    p.add_argument("--seed", type=int, default=None,
                   help="Semilla RNG (opcional) para reproducibilidad")
    # Permite elegir subconjunto de zonas si quisieras (opcional)
    p.add_argument("--zones", nargs="*", default=ZONAS,
                   help="Zonas a monitorear (por defecto: todas)")
    return p.parse_args()

def validar_args(args):
    if args.duration <= 0:
        sys.exit("ERROR: --duration debe ser > 0.")
    if args.frequency <= 0:
        sys.exit("ERROR: --frequency debe ser > 0.")
    if args.frequency > args.duration:
        print("ADVERTENCIA: la frecuencia es mayor a la duración; es posible que cada cámara reporte 0 o 1 evento.")
    if not args.zones:
        sys.exit("ERROR: Debe haber al menos una zona.")
    # Verificar probabilidades
    total_prob = sum(PROBABILIDADES.values())
    if abs(total_prob - 1.0) > 1e-9:
        sys.exit(f"ERROR: Las probabilidades no suman 1 (suman {total_prob}). Ajusta PROBABILIDADES en el código.")

def main():
    # Recomendado en Windows para multiprocessing
    mp.set_start_method("spawn", force=True)

    args = parse_args()
    validar_args(args)

    # Semilla global (solo para repartir a cada proceso)
    master_seed = args.seed if args.seed is not None else int(time.time())
    rng = random.Random(master_seed)

    # Cola para resultados finales
    result_queue = mp.Queue()

    procesos = []
    print("=== Iniciando monitoreo de cámaras ===")
    print(f"Duración: {args.duration}s | Frecuencia: {args.frequency}s | Zonas: {', '.join(args.zones)}")
    print("Eventos posibles:", ", ".join(EVENTOS))
    print("Paranormales contabilizados:", ", ".join(sorted(PARANORMAL)))
    print("=" * 60)

    try:
        for cam_id, zona in enumerate(args.zones, start=1):
            seed_hijo = rng.randrange(1, 2**31 - 1)
            p = mp.Process(
                target=proceso_camara,
                args=(cam_id, zona, args.duration, args.frequency, result_queue, seed_hijo),
                name=f"Camara-{cam_id}-{zona}"
            )
            p.start()
            procesos.append(p)

        # Esperar a que terminen todos
        for p in procesos:
            p.join()

        # Recolectar resultados
        resultados = {}
        # Esperamos hasta N mensajes (uno por cámara) sin colgarnos si alguno faltara
        deadline = time.time() + 3.0
        while len(resultados) < len(procesos) and time.time() < deadline:
            try:
                cam_id, zona, paranormales, total = result_queue.get(timeout=0.2)
                resultados[cam_id] = (zona, paranormales, total)
            except queue.Empty:
                pass

        print("\n=== RESUMEN FINAL ===")
        total_paranormales = 0
        total_eventos = 0
        for cam_id in sorted(resultados.keys()):
            zona, paran, tot = resultados[cam_id]
            total_paranormales += paran
            total_eventos += tot
            print(f"CAM {cam_id:02d} | {zona:<12} -> Paranormales: {paran} / {tot}")

        # Si faltó alguno (por si acaso)
        faltantes = set(range(1, len(procesos) + 1)) - set(resultados.keys())
        for cam_id in sorted(faltantes):
            print(f"CAM {cam_id:02d} | (sin reporte final)")

        print("-" * 60)
        print(f"TOTAL eventos: {total_eventos} | TOTAL paranormales: {total_paranormales}")
        print("Fin del monitoreo.")
    except KeyboardInterrupt:
        print("\nInterrupción por usuario. Terminando procesos…")
        for p in procesos:
            if p.is_alive():
                p.terminate()
        for p in procesos:
            p.join()

if __name__ == "__main__":
    # Requerido en Windows para evitar forks implícitos
    if sys.platform.startswith("win"):
        import multiprocessing.spawn  # noqa: F401  (asegura registro correcto en pyinstaller, etc.)
    main()
