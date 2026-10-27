from enum import Enum
from dataclasses import dataclass
import datetime

class SeatState(Enum):
    AVAILABLE = "AVAILABLE"
    RESERVED = "RESERVED"

class ReservationResultType(Enum):
    SUCCESS = "EXITOSA"
    REJECTED = "RECHAZADA"

@dataclass
class ReservationRequest:
    request_id: int
    user_name: str
    movie_name: str
    function_time: str
    seat_id: str
    created_at: datetime.datetime
    
@dataclass
class ReservationResult:
    request: ReservationRequest
    status: ReservationResultType
    processed_at: datetime.datetime
