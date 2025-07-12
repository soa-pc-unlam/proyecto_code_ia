from multiprocessing import Process, current_process
import os

# Definición de la estructura del árbol
darbol = {
    "A": ["B"],
    "B": ["C", "D"],
    "C": ["E"],
    "D": ["F", "G"],
    "E": ["H", "I"],
    "F": [],
    "G": [],
    "H": [],
    "I": []
}

def crear_proceso(nombre, pid_padre=None):
    pid_actual = os.getpid()
    if pid_padre is None:
        pid_padre = os.getppid()
    print(f"Proceso {nombre} creado. PID: {pid_actual}, PID Padre: {pid_padre}")
    hijos = darbol.get(nombre, [])
    procesos = []
    for hijo in hijos:
        p = Process(target=crear_proceso, args=(hijo, pid_actual))
        p.start()
        procesos.append(p)
    for p in procesos:
        p.join()  # Espera a que todos los hijos terminen antes de finalizar el proceso actual

if __name__ == "__main__":
    print("Creando árbol de procesos...\n")
    crear_proceso("A")
    print("\nÁrbol de procesos creado correctamente.") 