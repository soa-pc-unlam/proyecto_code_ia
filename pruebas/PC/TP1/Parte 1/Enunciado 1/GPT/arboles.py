import multiprocessing
import time
import os

def proceso(nombre, hijos):
    pid = os.getpid()
    ppid = os.getppid()
    print(f"Proceso {nombre} iniciado (PID: {pid}, PPID: {ppid})")

    procesos = []
    for hijo in hijos:
        p = multiprocessing.Process(target=proceso, args=(hijo[0], hijo[1]))
        p.start()
        procesos.append(p)

    # Pausa para observar claramente la creación de hijos
    time.sleep(1)

    for p in procesos:
        p.join()

    print(f"Proceso {nombre} finalizado (PID: {pid})")

if __name__ == "__main__":
    multiprocessing.freeze_support()

    # Estructura del árbol: (nombre, [hijos])
    arbol = ("A", [
        ("B", [
            ("C", [
                ("E", [
                    ("H", []),
                    ("I", [])
                ])
            ]),
            ("D", [
                ("F", []),
                ("G", [])
            ])
        ])
    ])

    print("Iniciando creación del árbol de procesos...\n")
    proceso(arbol[0], arbol[1])
    print("\nÁrbol de procesos finalizado.")