"""
Simulación de un juego de dados entre varios jugadores usando procesos en paralelo.
Cada jugador es representado por un proceso independiente que lanza un dado varias veces,
suma los resultados y muestra su puntuación final. Al finalizar, el programa indica que todos los jugadores han terminado.
Esta versión utiliza os.fork() y solo funciona en sistemas Linux/Mac.
"""

import os
import random
import time
import sys

# Constantes: número de jugadores y lanzamientos
PLAYER = 5
THROWS = 10

def player(id):
    # Mensaje de entrada al juego
    sys.stdout.write(f"Jugador {id} entra al juego.\n")
    points = 0
    # Cada jugador lanza el dado varias veces
    for i in range(THROWS):
        dice = random.randint(1, 6)  # Lanzamiento de dado
        points += dice  # Suma de puntos
        sys.stdout.write(f"Jugador {id} - Lanzamiento {i+1}: {dice}\n")
        time.sleep(random.uniform(0.1, 0.3))  # Espera aleatoria para simular tiempo de juego
    # Mensaje de finalización con la puntuación total
    sys.stdout.write(f"Jugador {id} finaliza con {points} puntos.\n")


def main():
    children = []  # Lista para almacenar los PID de los procesos hijos
    # Crear un proceso hijo por cada jugador
    for i in range(PLAYER):
        pid = os.fork()
        if pid == 0:
            # Proceso hijo ejecuta la función del jugador
            player(i+1)
            os._exit(0)  # Termina el proceso hijo
        else:
            # Proceso padre almacena el PID del hijo
            children.append(pid)
    # Proceso padre espera a que todos los hijos terminen
    for pid in children:
        os.waitpid(pid, 0)
    print("Todos los jugadores han terminado.")

if __name__ == "__main__":
    main() 