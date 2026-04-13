import multiprocessing
import time
import random

# Zonas a monitorear
ZONAS = [
    "Sótano 2",
    "Ático",
    "Cocina",
    "Dormitorio",
    "Jardín",
    "Mausoleo"
]

# Eventos posibles
EVENTOS = [
    "Sin actividad",
    "Movimiento detectado",
    "Anomalía térmica",
    "Sombra extraña",
    "Ruido detectado"
]

# Definimos qué eventos se consideran paranormales (excepto "Sin actividad")
EVENTOS_PARANORMALES = {
    "Movimiento detectado",
    "Anomalía térmica",
    "Sombra extraña",
    "Ruido detectado"
}

def camara_monitor(id_camara: int, zona: str, duracion: int, frecuencia: int):
    """
    Función que simula el monitoreo de una cámara en un proceso independiente.
    :param id_camara: ID de la cámara (entero)
    :param zona: Zona que monitorea la cámara (cadena)
    :param duracion: Duración total del monitoreo en segundos (entero)
    :param frecuencia: Frecuencia de reporte en segundos (entero)
    """
    eventos_paranormales_detectados = 0
    tiempo_inicial = time.time()
    tiempo_final = tiempo_inicial + duracion

    while time.time() < tiempo_final:
        # Generar evento aleatorio para la cámara
        evento = random.choices(
            population=EVENTOS,
            weights=[0.5, 0.15, 0.1, 0.1, 0.15],  # Probabilidades a criterio, suman 1
            k=1
        )[0]

        # Imprimir mensaje con ID cámara, zona y evento
        print(f"CÁMARA {id_camara} | ZONA: {zona} | EVENTO: {evento}")

        # Contar si el evento es paranormal
        if evento in EVENTOS_PARANORMALES:
            eventos_paranormales_detectados += 1

        # Esperar la frecuencia antes del siguiente reporte
        time.sleep(frecuencia)

    # Al finalizar, informar la cantidad de eventos paranormales detectados
    print(f"CÁMARA {id_camara} | ZONA: {zona} | MONITOREO FINALIZADO. Eventos paranormales detectados: {eventos_paranormales_detectados}")

def main(duracion: int, frecuencia: int):
    """
    Función principal que crea los procesos para cada cámara y espera a que terminen.
    :param duracion: Duración total del monitoreo en segundos (entero)
    :param frecuencia: Frecuencia de reporte en segundos (entero)
    """
    procesos = []
    for i, zona in enumerate(ZONAS, start=1):
        p = multiprocessing.Process(target=camara_monitor, args=(i, zona, duracion, frecuencia))
        procesos.append(p)
        p.start()

    # Esperar a que todos los procesos finalicen
    for p in procesos:
        p.join()

    print("Monitoreo finalizado. Todas las cámaras han terminado.")

if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="Monitoreo de cámaras para detección de actividad paranormal.")
    parser.add_argument(
        "-d", "--duracion", type=int, required=True,
        help="Duración total del monitoreo en segundos."
    )
    parser.add_argument(
        "-f", "--frecuencia", type=int, required=True,
        help="Frecuencia con que las cámaras reportan eventos en segundos."
    )
    args = parser.parse_args()

    if args.duracion <= 0 or args.frecuencia <= 0:
        print("Error: La duración y la frecuencia deben ser números enteros positivos.")
        exit(1)

    main(args.duracion, args.frecuencia)