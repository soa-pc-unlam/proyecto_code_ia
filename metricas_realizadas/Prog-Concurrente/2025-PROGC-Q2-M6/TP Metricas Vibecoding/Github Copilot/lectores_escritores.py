import threading
import time
import random

# Constantes de configuración
NUM_LECTORES = 4  # Cambiar según necesidad
NUM_ESCRITORES = 2  # Cambiar según necesidad
TIEMPO_EJECUCION = 8  # Segundos que cada hilo estará activo
MIN_ACCESOS = 3
MAX_ACCESOS = 10

# Documento compartido y su historial
documento = {"contenido": '"Documento inicial"'}
historial_cambios = []
historial_lock = threading.Lock()

# Sincronización lectores-escritores
mutex = threading.Lock()
lectores = 0
lectores_lock = threading.Lock()
escritor_lock = threading.Lock()

def lector(id_lector):
    global lectores
    accesos = random.randint(MIN_ACCESOS, MAX_ACCESOS)
    tiempo_inicio = time.time()
    for i in range(accesos):
        if time.time() - tiempo_inicio > TIEMPO_EJECUCION:
            break
        time.sleep(random.uniform(0.2, 1.2))  # Simula acceso aleatorio

        with lectores_lock:
            lectores += 1
            if lectores == 1:
                escritor_lock.acquire()
        # Leer documento
        print(f"Lector numero {id_lector} lee {documento['contenido']}")
        with lectores_lock:
            lectores -= 1
            if lectores == 0:
                escritor_lock.release()

def escritor(id_escritor):
    accesos = random.randint(MIN_ACCESOS, MAX_ACCESOS)
    tiempo_inicio = time.time()
    for i in range(accesos):
        if time.time() - tiempo_inicio > TIEMPO_EJECUCION:
            break
        time.sleep(random.uniform(0.5, 1.5))  # Simula acceso aleatorio

        escritor_lock.acquire()
        nuevo_contenido = f'"Escrito por escritor {id_escritor} acceso {i+1}"'
        documento['contenido'] = nuevo_contenido
        print(f"Escritor numero {id_escritor} escribiendo {nuevo_contenido}")
        with historial_lock:
            historial_cambios.append((id_escritor, nuevo_contenido))
        escritor_lock.release()

def main():
    hilos = []

    # Crear hilos lectores
    for i in range(1, NUM_LECTORES + 1):
        t = threading.Thread(target=lector, args=(i,))
        hilos.append(t)

    # Crear hilos escritores
    for i in range(1, NUM_ESCRITORES + 1):
        t = threading.Thread(target=escritor, args=(i,))
        hilos.append(t)

    # Iniciar todos los hilos
    for t in hilos:
        t.start()

    # Esperar a que todos terminen
    for t in hilos:
        t.join()

    # Mostrar resumen de cambios
    print("\nResumen de cambios realizados por los escritores:")
    for idx, (id_escritor, contenido) in enumerate(historial_cambios, 1):
        print(f"{idx}. Escritor {id_escritor}: {contenido}")

if __name__ == "__main__":
    main()