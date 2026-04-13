"""
Simulación de un juego de dados entre varios jugadores usando procesos en paralelo.
Cada jugador es representado por un proceso independiente que lanza un dado varias veces,
suma los resultados y muestra su puntuación final. Al finalizar, el programa indica que todos los jugadores han terminado.
Esta versión está adaptada para funcionar en Windows usando multiprocessing.Process.
"""

from multiprocessing import Process
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
    procesos = []  # Lista para almacenar los procesos
    # Crear y arrancar un proceso por cada jugador
    for i in range(PLAYER):
        p = Process(target=player, args=(i+1,))
        procesos.append(p)
        p.start()
    # Esperar a que todos los procesos terminen
    for p in procesos:
        p.join()
    print("Todos los jugadores han terminado.")

if __name__ == "__main__":
    main() 