import asyncio
import logging
import os
from contextlib import asynccontextmanager

from fastapi import (
    FastAPI, Depends, HTTPException, Query, Request, WebSocket, WebSocketDisconnect, status
)
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from slowapi import Limiter, _rate_limit_exceeded_handler
from slowapi.errors import RateLimitExceeded
from slowapi.util import get_remote_address

from config import FRONTEND_DIR, LOG_DIR, LOGIN_RATE_LIMIT
from database import init_pool, close_pool, get_cursor
from auth import authenticate_user, create_access_token, get_current_user
from models import LoginRequest, ReserveSeatsRequest, ReleaseSeatsRequest, PaymentRequest
from ws_manager import manager as ws_manager
from seat_service import (
    get_concert_seats, reserve_seats, release_seats,
    confirm_purchase, get_user_reserved_seats,
)
from payment_stub import process_payment
from cleaner import start_cleaner, stop_cleaner
from concert_loader import load_all_concerts

os.makedirs(LOG_DIR, exist_ok=True)
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# Lifespan: startup / shutdown
# ---------------------------------------------------------------------------

@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Iniciando Sistema de Venta de Entradas…")
    init_pool()
    load_all_concerts()
    # get_running_loop() is the correct call inside an async context (Python 3.10+)
    ws_manager.set_event_loop(asyncio.get_running_loop())
    start_cleaner()
    logger.info("Sistema iniciado correctamente.")
    yield
    logger.info("Deteniendo sistema…")
    stop_cleaner()
    close_pool()
    logger.info("Sistema detenido.")


# ---------------------------------------------------------------------------
# App
# ---------------------------------------------------------------------------

app = FastAPI(
    title="Sistema de Venta de Entradas para Recitales",
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Rate limiting
limiter = Limiter(key_func=get_remote_address)
app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)


# ---------------------------------------------------------------------------
# Auth
# ---------------------------------------------------------------------------

@app.post("/api/auth/login")
@limiter.limit(LOGIN_RATE_LIMIT)
def login(request: Request, body: LoginRequest):
    user = authenticate_user(body.username, body.password)
    if not user:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Usuario o contraseña incorrectos.",
        )
    # Username is embedded in the token to avoid a DB query on every authenticated request
    token = create_access_token({"sub": user["id"], "username": user["username"]})
    return {
        "access_token": token,
        "token_type": "bearer",
        "user": {
            "id": user["id"],
            "username": user["username"],
            "email": user.get("email"),
            "full_name": user.get("full_name"),
        },
    }


@app.get("/api/auth/me")
def get_me(current_user: dict = Depends(get_current_user)):
    # This endpoint needs the full user record → hit the DB
    with get_cursor(commit=False) as (cur, conn):
        cur.execute(
            "SELECT id, username, email, full_name FROM users WHERE id = %s",
            (current_user["id"],),
        )
        user = cur.fetchone()
        if user is None:
            raise HTTPException(status_code=404, detail="Usuario no encontrado.")
        return dict(user)


# ---------------------------------------------------------------------------
# Concerts
# ---------------------------------------------------------------------------

@app.get("/api/concerts")
def list_concerts(
    skip: int = Query(default=0, ge=0),
    limit: int = Query(default=50, ge=1, le=200),
):
    with get_cursor(commit=False) as (cur, conn):
        cur.execute(
            """
            SELECT c.id, c.name, c.artist, c.event_date, c.venue, c.description,
                   COUNT(CASE WHEN s.status = 'available' THEN 1 END) AS available_seats,
                   COUNT(s.id)                                         AS total_seats
            FROM concerts c
            LEFT JOIN seats s ON s.concert_id = c.id
            WHERE c.is_active = TRUE
            GROUP BY c.id
            ORDER BY c.event_date
            LIMIT %s OFFSET %s
            """,
            (limit, skip),
        )
        return [dict(row) for row in cur.fetchall()]


@app.get("/api/concerts/{concert_id}")
def get_concert(concert_id: int):
    with get_cursor(commit=False) as (cur, conn):
        cur.execute(
            """
            SELECT c.id, c.name, c.artist, c.event_date, c.venue, c.description,
                   COUNT(CASE WHEN s.status = 'available' THEN 1 END) AS available_seats,
                   COUNT(s.id)                                         AS total_seats
            FROM concerts c
            LEFT JOIN seats s ON s.concert_id = c.id
            WHERE c.id = %s AND c.is_active = TRUE
            GROUP BY c.id
            """,
            (concert_id,),
        )
        concert = cur.fetchone()
    if not concert:
        raise HTTPException(status_code=404, detail="Recital no encontrado.")
    return dict(concert)


@app.get("/api/concerts/{concert_id}/seats")
def list_seats(concert_id: int):
    return get_concert_seats(concert_id)


@app.get("/api/concerts/{concert_id}/my-seats")
def my_reserved_seats(
    concert_id: int, current_user: dict = Depends(get_current_user)
):
    return get_user_reserved_seats(current_user["id"], concert_id)


# ---------------------------------------------------------------------------
# Seats
# ---------------------------------------------------------------------------

@app.post("/api/seats/reserve")
def reserve(
    request: ReserveSeatsRequest, current_user: dict = Depends(get_current_user)
):
    if not request.seat_ids:
        raise HTTPException(status_code=400, detail="No se especificaron asientos.")

    success, message, reserved = reserve_seats(
        seat_ids=request.seat_ids,
        user_id=current_user["id"],
        username=current_user["username"],
        concert_id=request.concert_id,
    )
    if not success:
        raise HTTPException(status_code=409, detail=message)

    return {"message": message, "reserved_seats": reserved}


@app.post("/api/seats/release")
def release(
    request: ReleaseSeatsRequest, current_user: dict = Depends(get_current_user)
):
    success, message = release_seats(
        seat_ids=request.seat_ids,
        user_id=current_user["id"],
        concert_id=request.concert_id,
    )
    return {"message": message}


# ---------------------------------------------------------------------------
# Payment
# ---------------------------------------------------------------------------

@app.post("/api/payment/process")
async def process(
    request: PaymentRequest, current_user: dict = Depends(get_current_user)
):
    with get_cursor(commit=False) as (cur, conn):
        cur.execute(
            """
            SELECT COALESCE(SUM(price), 0) AS total
            FROM seats
            WHERE id = ANY(%s) AND reserved_by = %s AND status = 'reserved'
            """,
            (request.seat_ids, current_user["id"]),
        )
        total = float(cur.fetchone()["total"])

    if total == 0:
        raise HTTPException(
            status_code=400,
            detail="No se encontraron asientos reservados válidos para este usuario.",
        )

    # await: does not block the event loop during payment latency simulation
    payment_result = await process_payment(current_user["id"], total, request.payment_method)

    success, message = confirm_purchase(
        seat_ids=request.seat_ids,
        user_id=current_user["id"],
        concert_id=request.concert_id,
    )
    if not success:
        raise HTTPException(status_code=400, detail=message)

    return {"message": message, "transaction": payment_result}


# ---------------------------------------------------------------------------
# WebSocket — real-time seat updates
# ---------------------------------------------------------------------------

@app.websocket("/ws/{concert_id}")
async def websocket_endpoint(websocket: WebSocket, concert_id: int):
    await ws_manager.connect(concert_id, websocket)
    logger.info(
        f"WS conectado — recital {concert_id} "
        f"({ws_manager.get_connection_count(concert_id)} conexión/es activa/s)"
    )
    try:
        while True:
            await websocket.receive_text()  # keep-alive
    except WebSocketDisconnect:
        ws_manager.disconnect(concert_id, websocket)
        logger.info(f"WS desconectado — recital {concert_id}")


# ---------------------------------------------------------------------------
# Race condition log (for admin / debug)
# ---------------------------------------------------------------------------

@app.get("/api/admin/race-conditions")
def race_conditions(
    current_user: dict = Depends(get_current_user),
    limit: int = Query(default=50, ge=1, le=500),
):
    with get_cursor(commit=False) as (cur, conn):
        cur.execute(
            """
            SELECT rcl.id, rcl.seat_id, rcl.loser_user_id, rcl.loser_username,
                   rcl.thread_name, rcl.occurred_at,
                   s.section, s.row_label, s.seat_number, c.name AS concert_name
            FROM race_condition_log rcl
            LEFT JOIN seats s ON s.id = rcl.seat_id
            LEFT JOIN concerts c ON c.id = s.concert_id
            ORDER BY rcl.occurred_at DESC
            LIMIT %s
            """,
            (limit,),
        )
        return [dict(row) for row in cur.fetchall()]


# ---------------------------------------------------------------------------
# Frontend static serving
# ---------------------------------------------------------------------------

@app.get("/")
async def root():
    return FileResponse(os.path.join(FRONTEND_DIR, "index.html"))


@app.get("/concerts")
async def concerts_page():
    return FileResponse(os.path.join(FRONTEND_DIR, "concerts.html"))


@app.get("/seats")
async def seats_page():
    return FileResponse(os.path.join(FRONTEND_DIR, "seats.html"))


@app.get("/payment")
async def payment_page():
    return FileResponse(os.path.join(FRONTEND_DIR, "payment.html"))


app.mount(
    "/static",
    StaticFiles(directory=os.path.join(FRONTEND_DIR, "static")),
    name="static",
)
