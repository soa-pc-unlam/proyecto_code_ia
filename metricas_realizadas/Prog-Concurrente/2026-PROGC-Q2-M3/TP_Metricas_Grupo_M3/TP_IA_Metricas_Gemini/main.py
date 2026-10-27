import os
import multiprocessing
from cinema import Cinema
from reservation_system import ReservationSystem
from ui import CinemaApp

def main():
    print(f"--- CINEMA RESERVATION SYSTEM (CONCURRENTE) ---")
    print(f"PID del proceso: {os.getpid()}")
    print(f"CPUs lógicas detectadas: {multiprocessing.cpu_count()}")
    
    num_workers = 4
    print(f"Workers configurados: {num_workers}")
    print("-----------------------------------------------")
    
    # 1. Crear el modelo del cine
    cinema = Cinema()
    
    # 2. Inicializar sistema de colas y workers
    system = ReservationSystem(cinema, num_workers=num_workers)
    system.start()
    
    # 3. Lanzar interfaz gráfica
    app = CinemaApp(cinema, system)
    app.protocol("WM_DELETE_WINDOW", app.on_closing)
    app.mainloop()

if __name__ == "__main__":
    main()
