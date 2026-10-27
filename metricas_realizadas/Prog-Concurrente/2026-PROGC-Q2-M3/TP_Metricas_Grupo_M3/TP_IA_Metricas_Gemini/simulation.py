import threading
import time
import random
import datetime
from models import ReservationRequest
from reservation_system import ReservationSystem
from cinema import Cinema

class Producer(threading.Thread):
    def __init__(self, producer_id: int, system: ReservationSystem, cinema: Cinema, 
                 num_requests: int, scenario: str):
        super().__init__(name=f"Producer-{producer_id}")
        self.producer_id = producer_id
        self.system = system
        self.cinema = cinema
        self.num_requests = num_requests
        self.scenario = scenario
        self.is_running = True

    def run(self):
        for i in range(self.num_requests):
            if not self.is_running:
                break
                
            if self.scenario == "DISTRIBUTED":
                # Seleccionar asiento aleatorio
                movie_name = random.choice(list(self.cinema.movies.keys()))
                func = random.choice(list(self.cinema.movies[movie_name].functions.keys()))
                function_time = func
                seat_id = random.choice(list(self.cinema.movies[movie_name].functions[function_time].seats.keys()))
            else:
                # ALTA CONTENCIÓN: Todos intentan reservar el mismo asiento
                movie_name = "Matrix"
                function_time = "20:00"
                seat_id = "A1"
            
            req = ReservationRequest(
                request_id=random.randint(100000, 999999),
                user_name=f"SimUser-{self.producer_id}",
                movie_name=movie_name,
                function_time=function_time,
                seat_id=seat_id,
                created_at=datetime.datetime.now()
            )
            
            # Se genera y encola la solicitud de manera independiente (Productores)
            self.system.submit_request(req)
            
            # Simulamos el tiempo de "pensar" de un usuario
            time.sleep(random.uniform(0.001, 0.01))

    def stop(self):
        self.is_running = False

class Simulator:
    def __init__(self, system: ReservationSystem, cinema: Cinema):
        self.system = system
        self.cinema = cinema
        self.producers = []
        self.start_time = None
        self.end_time = None

    def run_simulation(self, num_producers: int, requests_per_producer: int, scenario: str):
        self.start_time = time.time()
        self.producers = []
        
        # Lanzar threads productores
        for i in range(num_producers):
            p = Producer(i+1, self.system, self.cinema, requests_per_producer, scenario)
            self.producers.append(p)
            p.start()
            
        # Esperar a que terminen de encolar las solicitudes
        for p in self.producers:
            p.join()
            
        # Esperar a que los consumidores terminen de procesar toda la cola
        self.system.request_queue.join()
        self.end_time = time.time()
        
    def stop(self):
        for p in self.producers:
            p.stop()
        
    def run_conflict_test(self):
        """Prueba de correctitud concurrente: 20 solicitudes concurrentes al mismo asiento"""
        movie = "Matrix"
        func = "20:00"
        self.cinema.reset_function(movie, func)
        
        self.start_time = time.time()
        threads = []
        
        for i in range(20):
            req = ReservationRequest(
                request_id=random.randint(100000, 999999),
                user_name=f"TestUser-{i+1}",
                movie_name=movie,
                function_time=func,
                seat_id="A1",
                created_at=datetime.datetime.now()
            )
            # Creamos threads que encolen casi al mismo tiempo para maximizar la concurrencia
            t = threading.Thread(target=self.system.submit_request, args=(req,))
            threads.append(t)
            
        for t in threads:
            t.start()
            
        for t in threads:
            t.join()
            
        # Esperar que se procesen las 20 peticiones
        self.system.request_queue.join()
        self.end_time = time.time()
