import threading
from models import SeatState

class Seat:
    def __init__(self, seat_id: str):
        self.seat_id = seat_id
        self.state = SeatState.AVAILABLE
        # Lock individual por asiento, como lo requieren las especificaciones
        self.lock = threading.Lock()

class Function:
    def __init__(self, time: str, rows: int = 5, cols: int = 6):
        self.time = time
        self.seats = {}
        # 5 filas x 6 columnas = 30 asientos
        for r in range(rows):
            row_letter = chr(65 + r) # A, B, C...
            for c in range(1, cols + 1):
                seat_id = f"{row_letter}{c}"
                self.seats[seat_id] = Seat(seat_id)

    def reset(self):
        """Reinicia el estado de todos los asientos de la función, usado para pruebas."""
        for seat in self.seats.values():
            with seat.lock:
                seat.state = SeatState.AVAILABLE

class Movie:
    def __init__(self, name: str, functions: list[str]):
        self.name = name
        self.functions = {f_time: Function(f_time) for f_time in functions}

class Cinema:
    def __init__(self):
        # 3 películas con 2 funciones cada una
        self.movies = {
            "Matrix": Movie("Matrix", ["18:00", "20:00"]),
            "Inception": Movie("Inception", ["19:00", "21:30"]),
            "Interstellar": Movie("Interstellar", ["17:00", "22:00"])
        }

    def get_seat(self, movie_name: str, function_time: str, seat_id: str) -> Seat | None:
        movie = self.movies.get(movie_name)
        if not movie:
            return None
        function = movie.functions.get(function_time)
        if not function:
            return None
        return function.seats.get(seat_id)
    
    def reset_function(self, movie_name: str, function_time: str):
        movie = self.movies.get(movie_name)
        if movie:
            func = movie.functions.get(function_time)
            if func:
                func.reset()

    def reset_all(self):
        """Reinicia todos los asientos de todas las películas y funciones."""
        for movie in self.movies.values():
            for func in movie.functions.values():
                func.reset()

