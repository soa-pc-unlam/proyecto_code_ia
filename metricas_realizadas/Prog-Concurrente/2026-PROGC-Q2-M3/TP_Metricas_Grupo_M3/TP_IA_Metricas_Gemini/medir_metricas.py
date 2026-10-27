import os
import sys
import time
import argparse
import threading
import psutil
from cinema import Cinema
from reservation_system import ReservationSystem
from simulation import Simulator

def monitor_process(pid, stop_event, sample_interval=0.05):
    """
    Monitorea el proceso dado recolectando muestras de CPU y memoria (RSS).
    Modo CPU: 100% = 1 núcleo (comportamiento estándar de psutil.Process.cpu_percent).
    """
    proc = psutil.Process(pid)
    proc.cpu_percent(interval=None) # Inicializar contador de psutil
    
    cpu_samples = []
    mem_samples = []
    
    while not stop_event.is_set():
        try:
            cpu = proc.cpu_percent(interval=None)
            mem_mb = proc.memory_info().rss / (1024 * 1024)
            
            cpu_samples.append(cpu)
            mem_samples.append(mem_mb)
        except (psutil.NoSuchProcess, psutil.AccessDenied):
            break
        time.sleep(sample_interval)
        
    avg_cpu = sum(cpu_samples) / len(cpu_samples) if cpu_samples else 0.0
    peak_cpu = max(cpu_samples) if cpu_samples else 0.0
    avg_mem = sum(mem_samples) / len(mem_samples) if mem_samples else 0.0
    peak_mem = max(mem_samples) if mem_samples else 0.0
    
    return {
        "avg_cpu": avg_cpu,
        "peak_cpu": peak_cpu,
        "avg_mem": avg_mem,
        "peak_mem": peak_mem,
        "samples": len(cpu_samples)
    }

def run_single_benchmark(num_users, reqs_per_user, num_workers):
    """Ejecuta una corrida completa de Acción 1 y Acción 2 con un sistema limpio."""
    pid = os.getpid()
    
    # 1. Preparar Sistema
    cinema = Cinema()
    system = ReservationSystem(cinema, num_workers=num_workers)
    system.start()
    simulator = Simulator(system, cinema)
    
    time.sleep(0.3)
    
    # --- ACCIÓN 1: CARGA DISTRIBUIDA ---
    cinema.reset_all()
    stop_event_1 = threading.Event()
    res_1 = {}
    
    def monitor_1():
        nonlocal res_1
        res_1 = monitor_process(pid, stop_event_1)
        
    t_mon_1 = threading.Thread(target=monitor_1)
    t_mon_1.start()
    
    t_start_1 = time.time()
    simulator.run_simulation(num_users, reqs_per_user, scenario="DISTRIBUTED")
    t_elapsed_1 = time.time() - t_start_1
    
    time.sleep(0.15)
    stop_event_1.set()
    t_mon_1.join()
    res_1["time"] = t_elapsed_1
    
    # Detener y reiniciar sistema para aislamiento estricto
    system.stop()
    time.sleep(0.4)
    
    # --- ACCIÓN 2: ALTA CONTENCIÓN ---
    cinema.reset_all()
    system_2 = ReservationSystem(cinema, num_workers=num_workers)
    system_2.start()
    simulator_2 = Simulator(system_2, cinema)
    
    stop_event_2 = threading.Event()
    res_2 = {}
    
    def monitor_2():
        nonlocal res_2
        res_2 = monitor_process(pid, stop_event_2)
        
    t_mon_2 = threading.Thread(target=monitor_2)
    t_mon_2.start()
    
    t_start_2 = time.time()
    simulator_2.run_simulation(num_users, reqs_per_user, scenario="CONTENTION")
    t_elapsed_2 = time.time() - t_start_2
    
    time.sleep(0.15)
    stop_event_2.set()
    t_mon_2.join()
    res_2["time"] = t_elapsed_2
    
    system_2.stop()
    time.sleep(0.3)
    
    return res_1, res_2

def run_automated_benchmark(num_users=30, reqs_per_user=20, repetitions=1, num_workers=4, codigo="M1"):
    pid = os.getpid()
    logical_cpus = psutil.cpu_count(logical=True)
    total_mem_mb = psutil.virtual_memory().total / (1024 * 1024)
    total_reqs = num_users * reqs_per_user
    
    print("=" * 75)
    print("INICIANDO MEDICIÓN AUTOMATIZADA CON PSUTIL")
    print(f"PID: {pid} | CPUs Lógicas: {logical_cpus} | Memoria RAM Total: {total_mem_mb:.0f} MB")
    print(f"Configuración: {num_users} usuarios | {reqs_per_user} reqs/usuario ({total_reqs} total) | {num_workers} workers")
    print(f"Repeticiones programadas: {repetitions}")
    print("=" * 75)
    
    runs_acc1 = []
    runs_acc2 = []
    
    for i in range(1, repetitions + 1):
        if repetitions > 1:
            print(f"\n>>> [Corrida {i}/{repetitions}] Ejecutando...")
        else:
            print(f"\n>>> Ejecutando pruebas...")
            
        r1, r2 = run_single_benchmark(num_users, reqs_per_user, num_workers)
        runs_acc1.append(r1)
        runs_acc2.append(r2)
        
        print(f"  [Acción 1 - Carga Distribuida] {r1['time']:.2f}s | CPU Prom: {r1['avg_cpu']:.1f}% (Pico: {r1['peak_cpu']:.1f}%) | RAM: {r1['peak_mem']:.0f} MB")
        print(f"  [Acción 2 - Alta Contención ] {r2['time']:.2f}s | CPU Prom: {r2['avg_cpu']:.1f}% (Pico: {r2['peak_cpu']:.1f}%) | RAM: {r2['peak_mem']:.0f} MB")
    
    # Consolidar métricas entre todas las repeticiones
    avg_cpu_acc1 = sum(r["avg_cpu"] for r in runs_acc1) / len(runs_acc1)
    peak_cpu_acc1 = max(r["peak_cpu"] for r in runs_acc1)
    peak_mem_acc1 = max(r["peak_mem"] for r in runs_acc1)
    avg_time_acc1 = sum(r["time"] for r in runs_acc1) / len(runs_acc1)
    
    avg_cpu_acc2 = sum(r["avg_cpu"] for r in runs_acc2) / len(runs_acc2)
    peak_cpu_acc2 = max(r["peak_cpu"] for r in runs_acc2)
    peak_mem_acc2 = max(r["peak_mem"] for r in runs_acc2)
    avg_time_acc2 = sum(r["time"] for r in runs_acc2) / len(runs_acc2)
    
    # Imprimir resumen de la planilla
    print("\n" + "=" * 75)
    print(f"RESULTADOS CONSOLIDADOS ({repetitions} repetición/es):")
    print("=" * 75)
    print(f"Codigo:            {codigo}")
    print(f"Método medición:   psutil")
    print(f"Modo CPU:          100%=1 núcleo")
    print(f"CPUs lógicas:      {logical_cpus}")
    print(f"Acción 1:          Carga Distribuida ({total_reqs} reqs, {avg_time_acc1:.2f}s prom)")
    print(f"CPU Acc1:          {avg_cpu_acc1:.1f}% (Pico máximo: {peak_cpu_acc1:.1f}%)")
    print(f"Acción 2:          Alta Contención ({total_reqs} reqs, {avg_time_acc2:.2f}s prom)")
    print(f"CPU Acc2:          {avg_cpu_acc2:.1f}% (Pico máximo: {peak_cpu_acc2:.1f}%)")
    print(f"Memoria Acc1 (MB): {peak_mem_acc1:.0f} MB")
    print(f"Memoria Acc2 (MB): {peak_mem_acc2:.0f} MB")
    print(f"Memoria Total (MB):{total_mem_mb:.0f} MB")
    print("=" * 75)
    
    print("\nFila en formato TSV (Lista para copiar y pegar directamente en Excel / Google Sheets):")
    row_tsv = f"{codigo}\tpsutil\t100%=1 núcleo\t{logical_cpus}\tCarga Distribuida\t{avg_cpu_acc1:.1f}%\tAlta Contención\t{avg_cpu_acc2:.1f}%\t{peak_mem_acc1:.0f}\t{peak_mem_acc2:.0f}\t{total_mem_mb:.0f}"
    print(row_tsv)
    print("=" * 75)

def monitor_external_pid(pid, codigo="M1"):
    """Permite monitorear la aplicación de interfaz gráfica mientras el usuario la usa."""
    proc = psutil.Process(pid)
    logical_cpus = psutil.cpu_count(logical=True)
    total_mem_mb = psutil.virtual_memory().total / (1024 * 1024)
    
    print("=" * 75)
    print(f"MONITORIZANDO PROCESO EXTERNO PID: {pid} ({proc.name()})")
    print(f"CPUs Lógicas: {logical_cpus} | Memoria RAM Total: {total_mem_mb:.0f} MB")
    print("=" * 75)
    
    # Acción 1
    input("\nPresiona ENTER cuando estés listo para comenzar a medir ACCIÓN 1 (Carga Distribuida)...")
    print(">> Midiendo Acción 1... (presiona ENTER para finalizar)")
    stop_event_1 = threading.Event()
    res_1 = {}
    def mon1():
        nonlocal res_1
        res_1 = monitor_process(pid, stop_event_1)
    t1 = threading.Thread(target=mon1)
    t1.start()
    input()
    stop_event_1.set()
    t1.join()
    print(f"-> Acción 1 finalizada. CPU: {res_1['avg_cpu']:.1f}% (Pico: {res_1['peak_cpu']:.1f}%) | RAM: {res_1['peak_mem']:.0f} MB")
    
    # Acción 2
    input("\nPresiona ENTER cuando estés listo para comenzar a medir ACCIÓN 2 (Alta Contención)...")
    print(">> Midiendo Acción 2... (presiona ENTER para finalizar)")
    stop_event_2 = threading.Event()
    res_2 = {}
    def mon2():
        nonlocal res_2
        res_2 = monitor_process(pid, stop_event_2)
    t2 = threading.Thread(target=mon2)
    t2.start()
    input()
    stop_event_2.set()
    t2.join()
    print(f"-> Acción 2 finalizada. CPU: {res_2['avg_cpu']:.1f}% (Pico: {res_2['peak_cpu']:.1f}%) | RAM: {res_2['peak_mem']:.0f} MB")
    
    print("\n" + "=" * 75)
    print("DATOS PARA COMPLETAR LA PLANILLA:")
    print("=" * 75)
    print(f"Codigo:            {codigo}")
    print(f"Método medición:   psutil")
    print(f"Modo CPU:          100%=1 núcleo")
    print(f"CPUs lógicas:      {logical_cpus}")
    print(f"Acción 1:          Carga Distribuida")
    print(f"CPU Acc1:          {res_1['avg_cpu']:.1f}% (Pico: {res_1['peak_cpu']:.1f}%)")
    print(f"Acción 2:          Alta Contención")
    print(f"CPU Acc2:          {res_2['avg_cpu']:.1f}% (Pico: {res_2['peak_cpu']:.1f}%)")
    print(f"Memoria Acc1 (MB): {res_1['peak_mem']:.0f} MB")
    print(f"Memoria Acc2 (MB): {res_2['peak_mem']:.0f} MB")
    print(f"Memoria Total (MB):{total_mem_mb:.0f} MB")
    print("=" * 75)
    
    row_tsv = f"{codigo}\tpsutil\t100%=1 núcleo\t{logical_cpus}\tCarga Distribuida\t{res_1['avg_cpu']:.1f}%\tAlta Contención\t{res_2['avg_cpu']:.1f}%\t{res_1['peak_mem']:.0f}\t{res_2['peak_mem']:.0f}\t{total_mem_mb:.0f}"
    print(f"\nFila en formato TSV:\n{row_tsv}\n" + "=" * 75)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Medición de métricas con psutil para TP Concurrente")
    parser.add_argument("-u", "--usuarios", "--users", type=int, default=30,
                        help="Cantidad de usuarios productores (default: 30)")
    parser.add_argument("-s", "--solicitudes", "--reqs", type=int, default=20,
                        help="Cantidad de solicitudes por usuario (default: 20)")
    parser.add_argument("-r", "--repeticiones", "--runs", type=int, default=1,
                        help="Cantidad de repeticiones de la prueba (default: 1)")
    parser.add_argument("-w", "--workers", type=int, default=4,
                        help="Cantidad de workers consumidores (default: 4)")
    parser.add_argument("-c", "--codigo", type=str, default="M1",
                        help="Código de identificación para la planilla (default: M1)")
    parser.add_argument("--pid", type=int, default=None,
                        help="PID de un proceso existente a monitorear (ej. la interfaz gráfica main.py)")
    
    args = parser.parse_args()
    
    if args.pid:
        monitor_external_pid(args.pid, codigo=args.codigo)
    else:
        run_automated_benchmark(
            num_users=args.usuarios,
            reqs_per_user=args.solicitudes,
            repetitions=args.repeticiones,
            num_workers=args.workers,
            codigo=args.codigo
        )
