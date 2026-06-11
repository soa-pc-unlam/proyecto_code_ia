from pydantic import BaseModel, Field
from typing import Optional, List
from datetime import datetime


class LoginRequest(BaseModel):
    username: str = Field(min_length=1, max_length=50)
    password: str = Field(min_length=1, max_length=128)


class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: dict


class ReserveSeatsRequest(BaseModel):
    seat_ids: List[int]
    concert_id: int


class ReleaseSeatsRequest(BaseModel):
    seat_ids: List[int]
    concert_id: int


class PaymentRequest(BaseModel):
    seat_ids: List[int]
    concert_id: int
    payment_method: str = "credit_card"
    card_last_four: Optional[str] = "0000"
