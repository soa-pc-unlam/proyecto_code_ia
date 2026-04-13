import multiprocessing
import random
import time
import argparse

ZONAS = [
    "Sótano 2",
    "Ático",
    "Cocina",
    "Dormitorio",
    "Jardín",
    "Mausoleo"
]

EVENTOS = [
    "Sin actividad",
    "Movimiento detectado",
    "Anomalía térmica",
    "Sombra extraña",
    "Ruido detectado"
]

EVENTOS_PARANORMALES = {"Movimiento detectado", "Anomalía térmica", "Sombra extraña", "Ruido detectado"}

# Probabilidades para cada evento (puedes ajustarlas)
PROBS_EVENTOS = [0.6, 0.14, 0.1, 0.08, 0.08]  # Sin actividad es más probable

def camara_proceso(id_camara, zona, duracion, frecuencia, resultado_queue):
    tiempo_inicio = time.time()
    tiempo_fin = tiempo_inicio + duracion
    eventos_paranormales = 0
    eventos_total = 0
    while time.time() < tiempo_fin:
        evento = random.choices(EVENTOS, weights=PROBS_EVENTOS)[0]
        eventos_total += 1
        print(f"ID CÁMARA {id_camara} | ZONA {zona} | EVENTO: {evento}")
        if evento in EVENTOS_PARANORMALES:
            eventos_paranormales += 1
        time.sleep(frecuencia)
    print(f"ID CÁMARA {id_camara} | ZONA {zona} | FIN DE MONITOREO: {eventos_paranormales} eventos paranormales detectados (de {eventos_total} reportes).")
    resultado_queue.put((id_camara, zona, eventos_paranormales, eventos_total))

def main(duracion, frecuencia):
    procesos = []
    resultado_queue = multiprocessing.Queue()
    for i, zona in enumerate(ZONAS):
        p = multiprocessing.Process(
            target=camara_proceso,
            args=(i+1, zona, duracion, frecuencia, resultado_queue)
        )
        procesos.append(p)
        p.start()
    # Esperar que todas las cámaras terminen
    for p in procesos:
        p.join()
    print("\nResumen de eventos paranormales detectados por cámara:")
    while not resultado_queue.empty():
        id_camara, zona, eventos_paranormales, eventos_total = resultado_queue.get()
        print(f"ID CÁMARA {id_camara} | ZONA {zona} | Eventos paranormales: {eventos_paranormales} / {eventos_total}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Monitoreo de cámaras en mansión paranormal")
    parser.add_argument("--duracion", type=int, required=True,
                        help="Duración del monitoreo por cámara (segundos)")
    parser.add_argument("--frecuencia", type=int, required=True,
                        help="Frecuencia de reportes por cámara (segundos)")
    args = parser.parse_args()
    main(args.duracion, args.frecuencia)