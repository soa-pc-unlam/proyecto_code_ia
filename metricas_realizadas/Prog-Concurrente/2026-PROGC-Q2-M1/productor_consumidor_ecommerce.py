"""
productor_consumidor_ecommerce.py
==================================

Simulacion de un sistema de procesamiento de pedidos de un e-commerce
mediante el patron Productor-Consumidor, con:

  - N hilos productores generando pedidos con prioridad urgente/normal.
  - M hilos consumidores procesando pedidos desde un buffer acotado.
  - Prioridad de extraccion para pedidos urgentes, con envejecimiento
    (aging) de los pedidos normales para evitar inanicion indefinida.
  - Sincronizacion exclusivamente con la biblioteca estandar `threading`
    (Lock + Condition), sin ninguna forma de espera activa (polling).
  - Apagado ordenado (graceful shutdown) mediante el patron "poison pill"
    una vez procesada una cantidad total configurable de pedidos.
  - Registro (logging) thread-safe de cada evento relevante.
  - Resumen final con metricas de throughput y tiempos de espera.

Generado segun el prompt tecnico del TP "Actividad Practica Obligatoria
N.1 - Programacion Concurrente" (VibeCoding).
"""

from __future__ import annotations

import logging
import random
import threading
import time
from dataclasses import dataclass


# ---------------------------------------------------------------------------
# CONFIGURACION (todos los parametros "configurables" que pide la consigna)
# ---------------------------------------------------------------------------

N_PRODUCTORES = 4                  # cantidad de hilos productores (minimo 3)
M_CONSUMIDORES = 3                 # cantidad de hilos consumidores (minimo 2)
CAPACIDAD_BUFFER = 10              # tamano maximo del buffer compartido
TOTAL_PEDIDOS = 50                 # cantidad total de pedidos a generar/procesar

PROB_PEDIDO_URGENTE = 0.2          # probabilidad de que un pedido nazca urgente
UMBRAL_ENVEJECIMIENTO_SEG = 3.0    # tiempo de espera tras el cual un pedido
                                    # normal se trata como urgente (aging)

INTERVALO_PRODUCCION_SEG = (0.1, 0.5)   # demora entre pedidos de un productor
DEMORA_PROCESAMIENTO_SEG = (0.2, 0.8)   # demora al procesar un pedido

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s.%(msecs)03d [%(threadName)-14s] %(message)s",
    datefmt="%H:%M:%S",
)


# ---------------------------------------------------------------------------
# Modelo de datos
# ---------------------------------------------------------------------------

@dataclass
class Pedido:
    """Representa un pedido del e-commerce viajando por el sistema."""
    id: int
    prioridad: str              # "urgente" | "normal" (prioridad ORIGINAL)
    id_productor: int
    timestamp_creacion: float   # time.monotonic(), para medir tiempos de espera


# ---------------------------------------------------------------------------
# Contador atomico: se usa para repartir los ids de pedido entre los
# productores. Se protege explicitamente con un Lock, en vez de confiar en
# el GIL de CPython, para que la exclusion mutua sea correcta y explicita
# (y no un detalle de implementacion del interprete).
# ---------------------------------------------------------------------------

class ContadorAtomico:
    def __init__(self, valor_inicial: int = 0):
        self._valor = valor_inicial
        self._lock = threading.Lock()

    def incrementar_si_hay_cupo(self, tope: int):
        """Incrementa y devuelve el nuevo valor, o None si ya se llego al tope.

        Es la unica forma de "reservar" un id de pedido: la comprobacion y
        el incremento ocurren atomicamente dentro del mismo lock, asi dos
        productores nunca pueden reservar el mismo id ni exceder el tope.
        """
        with self._lock:
            if self._valor >= tope:
                return None
            self._valor += 1
            return self._valor


# ---------------------------------------------------------------------------
# Buffer acotado con prioridad y envejecimiento (aging).
#
# Se implementa a mano (no con queue.Queue ni queue.PriorityQueue) para
# poder controlar explicitamente:
#   1) el bloqueo sin espera activa de productores/consumidores
#      (Lock + Condition de la biblioteca estandar), y
#   2) la logica de seleccion con envejecimiento, que una PriorityQueue
#      estandar no resuelve "gratis" (la restriccion de la consigna pide
#      justamente no delegarle esta logica sin mas).
# ---------------------------------------------------------------------------

class BufferDePedidos:
    def __init__(self, capacidad_maxima: int, umbral_envejecimiento_seg: float):
        self._capacidad_maxima = capacidad_maxima
        self._umbral_envejecimiento_seg = umbral_envejecimiento_seg
        self._pedidos: list[Pedido] = []

        # Un solo Lock protege todo el estado interno (_pedidos, _cerrado).
        # Una unica Condition asociada a ese lock coordina tanto a los
        # productores (que esperan "hay lugar") como a los consumidores
        # (que esperan "hay algo para sacar"). Usar una sola Condition en
        # vez de dos separadas evita inconsistencias entre el lock que
        # protege los datos y el que se usa para senalizar.
        self._lock = threading.Lock()
        self._condicion = threading.Condition(self._lock)
        self._cerrado = False

    # -- Productores ---------------------------------------------------

    def encolar(self, pedido: Pedido) -> bool:
        """Inserta un pedido, bloqueando (sin polling) si el buffer esta lleno.

        Devuelve False si el buffer se cerro mientras esperaba lugar (en
        este programa no deberia pasar, porque el cierre se ordena recien
        despues de que todos los productores terminaron, pero se deja el
        chequeo por seguridad y para dejar el mecanismo completo).
        """
        with self._condicion:
            # wait_for bloquea al hilo (libera el lock mientras espera) hasta
            # que el predicado sea verdadero; NO es un sondeo activo: el
            # hilo queda dormido hasta que algun notify_all() lo despierte.
            self._condicion.wait_for(
                lambda: len(self._pedidos) < self._capacidad_maxima or self._cerrado
            )
            if self._cerrado:
                return False
            self._pedidos.append(pedido)
            # Despierta a los consumidores que puedan estar esperando
            # "hay algo para sacar".
            self._condicion.notify_all()
            return True

    # -- Consumidores ----------------------------------------------------

    def _elegir_mejor_pedido(self) -> Pedido:
        """Elige que pedido sacar a continuacion.

        Regla: los pedidos urgentes (de origen, o "envejecidos" por haber
        esperado mas que UMBRAL_ENVEJECIMIENTO_SEG) van antes que los
        normales recientes; a igualdad de prioridad efectiva, gana el mas
        antiguo (FIFO). El envejecimiento es lo que garantiza que un
        pedido normal nunca quede esperando indefinidamente: eventualmente
        supera el umbral y pasa a competir en pie de igualdad con los
        urgentes.
        """
        ahora = time.monotonic()

        def es_urgente_efectivo(p: Pedido) -> bool:
            espera = ahora - p.timestamp_creacion
            return p.prioridad == "urgente" or espera >= self._umbral_envejecimiento_seg

        mejor = None
        mejor_es_urgente = False
        for pedido in self._pedidos:
            urgente = es_urgente_efectivo(pedido)
            if mejor is None:
                mejor, mejor_es_urgente = pedido, urgente
                continue
            if urgente and not mejor_es_urgente:
                mejor, mejor_es_urgente = pedido, urgente
            elif urgente == mejor_es_urgente and pedido.timestamp_creacion < mejor.timestamp_creacion:
                mejor, mejor_es_urgente = pedido, urgente
        return mejor

    def desencolar(self):
        """Extrae el proximo pedido a procesar, bloqueando sin polling si
        el buffer esta vacio. Devuelve None unicamente cuando el buffer
        esta vacio Y cerrado: esa es la senal de "poison pill" que le
        indica al consumidor que ya no va a llegar nada mas y puede
        terminar su ciclo de vida.
        """
        with self._condicion:
            while True:
                if self._pedidos:
                    pedido = self._elegir_mejor_pedido()
                    self._pedidos.remove(pedido)
                    # Despierta a los productores que puedan estar
                    # esperando "hay lugar".
                    self._condicion.notify_all()
                    return pedido
                if self._cerrado:
                    return None
                # Buffer vacio y todavia no cerrado: hay que esperar a que
                # llegue un pedido nuevo o se cierre el buffer. wait() (sin
                # timeout) bloquea al hilo por completo, no consume CPU.
                self._condicion.wait()

    # -- Cierre ordenado --------------------------------------------------

    def cerrar(self) -> None:
        """Marca el buffer como cerrado y despierta a todos los hilos que
        puedan estar esperando, para que reevaluen su condicion de salida.
        Se llama solo despues de haber esperado (join) a todos los
        productores, asi se garantiza que no va a encolarse nada mas.
        """
        with self._condicion:
            self._cerrado = True
            self._condicion.notify_all()


# ---------------------------------------------------------------------------
# Metricas: se acumulan en una lista protegida por su propio Lock. Cada
# consumidor registra un resultado al terminar de procesar un pedido; el
# resumen final se calcula recien al terminar toda la simulacion.
# ---------------------------------------------------------------------------

@dataclass
class RegistroPedidoCompletado:
    prioridad_original: str
    tiempo_espera_seg: float
    tiempo_procesamiento_seg: float


class RecolectorDeMetricas:
    def __init__(self):
        self._lock = threading.Lock()
        self._registros: list[RegistroPedidoCompletado] = []

    def registrar(self, registro: RegistroPedidoCompletado) -> None:
        with self._lock:
            self._registros.append(registro)

    def resumen(self) -> dict:
        with self._lock:
            registros = list(self._registros)  # copia para trabajar sin el lock

        resumen_por_prioridad = {}
        for prioridad in ("urgente", "normal"):
            del_grupo = [r for r in registros if r.prioridad_original == prioridad]
            cantidad = len(del_grupo)
            espera_promedio = (
                sum(r.tiempo_espera_seg for r in del_grupo) / cantidad if cantidad else 0.0
            )
            resumen_por_prioridad[prioridad] = {
                "cantidad": cantidad,
                "espera_promedio_seg": espera_promedio,
            }
        return {
            "total_procesados": len(registros),
            "por_prioridad": resumen_por_prioridad,
        }


# ---------------------------------------------------------------------------
# Hilos productores y consumidores
# ---------------------------------------------------------------------------

def rol_productor(
    id_productor: int,
    buffer: BufferDePedidos,
    contador_ids: ContadorAtomico,
    total_pedidos: int,
    rng: random.Random,
) -> None:
    while True:
        id_pedido = contador_ids.incrementar_si_hay_cupo(total_pedidos)
        if id_pedido is None:
            break  # ya se generaron todos los pedidos previstos

        prioridad = "urgente" if rng.random() < PROB_PEDIDO_URGENTE else "normal"
        pedido = Pedido(
            id=id_pedido,
            prioridad=prioridad,
            id_productor=id_productor,
            timestamp_creacion=time.monotonic(),
        )
        logging.info("Productor-%d genera pedido #%d (%s)", id_productor, pedido.id, pedido.prioridad)

        encolado = buffer.encolar(pedido)
        if not encolado:
            break  # el buffer se cerro mientras esperabamos lugar

        logging.info("Productor-%d encola pedido #%d", id_productor, pedido.id)
        time.sleep(rng.uniform(*INTERVALO_PRODUCCION_SEG))

    logging.info("Productor-%d finalizo: no quedan pedidos por generar", id_productor)


def rol_consumidor(
    id_consumidor: int,
    buffer: BufferDePedidos,
    metricas: RecolectorDeMetricas,
    rng: random.Random,
) -> None:
    while True:
        pedido = buffer.desencolar()
        if pedido is None:
            break  # poison pill: no hay mas pedidos ni va a haberlos

        tiempo_espera = time.monotonic() - pedido.timestamp_creacion
        logging.info(
            "Consumidor-%d toma pedido #%d (%s, espero %.2fs)",
            id_consumidor, pedido.id, pedido.prioridad, tiempo_espera,
        )

        inicio_proceso = time.monotonic()
        time.sleep(rng.uniform(*DEMORA_PROCESAMIENTO_SEG))
        tiempo_procesamiento = time.monotonic() - inicio_proceso

        metricas.registrar(RegistroPedidoCompletado(
            prioridad_original=pedido.prioridad,
            tiempo_espera_seg=tiempo_espera,
            tiempo_procesamiento_seg=tiempo_procesamiento,
        ))
        logging.info("Consumidor-%d termino pedido #%d", id_consumidor, pedido.id)

    logging.info("Consumidor-%d finalizo: buffer cerrado y vacio", id_consumidor)


# ---------------------------------------------------------------------------
# Orquestacion principal
# ---------------------------------------------------------------------------

def imprimir_encabezado() -> None:
    print("=" * 70)
    print("SIMULACION: Procesamiento concurrente de pedidos de un e-commerce")
    print("=" * 70)
    print(f"Productores: {N_PRODUCTORES}  |  Consumidores: {M_CONSUMIDORES}")
    print(f"Capacidad del buffer: {CAPACIDAD_BUFFER}  |  Total de pedidos: {TOTAL_PEDIDOS}")
    print(f"Probabilidad de pedido urgente: {PROB_PEDIDO_URGENTE:.0%}  |  "
          f"Umbral de envejecimiento: {UMBRAL_ENVEJECIMIENTO_SEG:.1f}s")
    print("=" * 70)
    print()


def imprimir_resumen(resumen: dict, duracion_total_seg: float) -> None:
    print()
    print("=" * 70)
    print("RESUMEN FINAL")
    print("=" * 70)
    print(f"Pedidos procesados en total: {resumen['total_procesados']}")
    for prioridad, datos in resumen["por_prioridad"].items():
        print(
            f"  - {prioridad.capitalize():8s}: {datos['cantidad']:3d} pedidos "
            f"| espera promedio en cola: {datos['espera_promedio_seg']:.2f}s"
        )
    throughput = resumen["total_procesados"] / duracion_total_seg if duracion_total_seg > 0 else 0.0
    print(f"Tiempo total de ejecucion: {duracion_total_seg:.2f}s")
    print(f"Throughput: {throughput:.2f} pedidos/segundo")
    print("=" * 70)


def main() -> None:
    imprimir_encabezado()

    buffer = BufferDePedidos(CAPACIDAD_BUFFER, UMBRAL_ENVEJECIMIENTO_SEG)
    contador_ids = ContadorAtomico()
    metricas = RecolectorDeMetricas()

    inicio = time.monotonic()

    hilos_productores = [
        threading.Thread(
            target=rol_productor,
            args=(i + 1, buffer, contador_ids, TOTAL_PEDIDOS, random.Random()),
            name=f"Productor-{i + 1}",
        )
        for i in range(N_PRODUCTORES)
    ]
    hilos_consumidores = [
        threading.Thread(
            target=rol_consumidor,
            args=(i + 1, buffer, metricas, random.Random()),
            name=f"Consumidor-{i + 1}",
        )
        for i in range(M_CONSUMIDORES)
    ]

    for hilo in hilos_productores + hilos_consumidores:
        hilo.start()

    # Se espera primero a los productores: una vez que todos terminaron,
    # esta garantizado que no se va a encolar ningun pedido mas.
    for hilo in hilos_productores:
        hilo.join()

    # Recien ahora se cierra el buffer (poison pill): los consumidores van
    # a seguir drenando lo que quede pendiente y, cuando el buffer quede
    # vacio, van a recibir la senal de cierre y terminar solos.
    buffer.cerrar()

    for hilo in hilos_consumidores:
        hilo.join()

    duracion_total = time.monotonic() - inicio
    resumen = metricas.resumen()
    imprimir_resumen(resumen, duracion_total)


if __name__ == "__main__":
    main()
