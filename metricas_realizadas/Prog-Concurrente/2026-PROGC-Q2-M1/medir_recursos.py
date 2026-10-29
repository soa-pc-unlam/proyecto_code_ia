# -*- coding: utf-8 -*-
"""
medir_recursos.py
==================

Mide CPU y memoria del programa productor_consumidor_ecommerce.py mientras
corre, usando psutil (midiendo el proceso DESDE AFUERA, como un observador
externo, que es la forma correcta de medir sin alterar el programa medido).

Corre dos escenarios:
  - Accion 1 (carga normal):  la configuracion por defecto del programa
    (4 productores, 3 consumidores, 50 pedidos).
  - Accion 2 (carga alta): una configuracion mas exigente (8 productores,
    6 consumidores, 300 pedidos, buffer mas grande), para ver como
    escalan el uso de CPU y memoria con la carga.

Guarda los resultados en resultados_metricas.json para despues volcarlos
a la planilla Excel.
"""

import json
import subprocess
import sys
import time

import psutil

PYTHON = sys.executable
SCRIPT = "productor_consumidor_ecommerce.py"


def correr_y_medir(nombre_escenario: str, overrides: dict) -> dict:
    """Lanza el programa como un PROCESO DEL SISTEMA OPERATIVO aparte
    (subprocess), con los parametros de `overrides` inyectados antes de
    llamar a main(), y lo monitorea desde afuera con psutil mientras
    corre, muestreando CPU% y memoria RSS cada 100 ms.
    """
    # Se arma un one-liner que importa el modulo, pisa las constantes de
    # configuracion que querramos (sin tocar el archivo original) y recien
    # ahi llama a main().
    partes = ["import productor_consumidor_ecommerce as pc"]
    partes += [f"pc.{clave} = {valor!r}" for clave, valor in overrides.items()]
    partes.append("pc.main()")
    codigo = "; ".join(partes)

    proceso = subprocess.Popen(
        [PYTHON, "-c", codigo],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    proc_psutil = psutil.Process(proceso.pid)

    muestras_cpu = []
    muestras_mem_mb = []

    proc_psutil.cpu_percent(interval=None)  # primer llamado "descarta" (warm-up de psutil)
    inicio = time.monotonic()

    while proceso.poll() is None:
        try:
            cpu = proc_psutil.cpu_percent(interval=0.1)  # % respecto de 1 nucleo
            mem_mb = proc_psutil.memory_info().rss / (1024 * 1024)
            muestras_cpu.append(cpu)
            muestras_mem_mb.append(mem_mb)
        except psutil.NoSuchProcess:
            break

    duracion = time.monotonic() - inicio
    proceso.wait()

    # Se descarta la primera muestra (suele incluir el arranque del
    # interprete, no el trabajo real del programa)
    muestras_cpu_utiles = muestras_cpu[1:] or muestras_cpu
    muestras_mem_utiles = muestras_mem_mb[1:] or muestras_mem_mb

    resultado = {
        "escenario": nombre_escenario,
        "overrides": overrides,
        "duracion_seg": round(duracion, 2),
        "cpu_promedio_pct": round(sum(muestras_cpu_utiles) / len(muestras_cpu_utiles), 2),
        "cpu_pico_pct": round(max(muestras_cpu_utiles), 2),
        "memoria_promedio_mb": round(sum(muestras_mem_utiles) / len(muestras_mem_utiles), 2),
        "memoria_pico_mb": round(max(muestras_mem_utiles), 2),
        "cantidad_muestras": len(muestras_cpu),
        "exit_code": proceso.returncode,
    }
    return resultado


def main():
    cpus_logicas = psutil.cpu_count(logical=True)
    memoria_total_mb = round(psutil.virtual_memory().total / (1024 * 1024))

    print(f"CPUs logicas detectadas: {cpus_logicas}")
    print(f"Memoria total del sistema: {memoria_total_mb} MB")
    print()

    escenarios = [
        ("Accion 1 - Carga normal (config. por defecto)", {}),
        (
            "Accion 2 - Carga alta",
            {
                "N_PRODUCTORES": 8,
                "M_CONSUMIDORES": 6,
                "CAPACIDAD_BUFFER": 20,
                "TOTAL_PEDIDOS": 300,
            },
        ),
    ]

    resultados = []
    for nombre, overrides in escenarios:
        print(f"--- Midiendo: {nombre} ---")
        r = correr_y_medir(nombre, overrides)
        resultados.append(r)
        print(json.dumps(r, indent=2, ensure_ascii=False))
        print()

    salida = {
        "cpus_logicas": cpus_logicas,
        "memoria_total_mb": memoria_total_mb,
        "resultados": resultados,
    }
    with open("resultados_metricas.json", "w", encoding="utf-8") as f:
        json.dump(salida, f, indent=2, ensure_ascii=False)

    print("Guardado en resultados_metricas.json")


if __name__ == "__main__":
    main()
