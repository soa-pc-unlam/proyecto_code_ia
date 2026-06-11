from typing import List, Tuple, Dict
from database import get_pool, get_cursor
from config import RESERVATION_TIMEOUT_MINUTES, POOL_ACQUIRE_TIMEOUT
import race_logger
from ws_manager import manager as ws_manager


def get_concert_seats(concert_id: int) -> List[Dict]:
    with get_cursor(commit=False) as (cur, conn):
        cur.execute(
            """
            SELECT id, section, row_label, seat_number, status, price,
                   reserved_by, reserved_at, reservation_expires_at
            FROM seats
            WHERE concert_id = %s
            ORDER BY section, row_label, CAST(seat_number AS INTEGER)
            """,
            (concert_id,),
        )
        return [dict(row) for row in cur.fetchall()]


def reserve_seats(
    seat_ids: List[int], user_id: int, username: str, concert_id: int
) -> Tuple[bool, str, List[Dict]]:
    """
    Atomically reserves one or more seats.

    Concurrency strategy: attempts the UPDATE directly (happy path, 1 query).
    Only if it fails (rowcount=0) does it run an extra SELECT to build the error message.

    Seat IDs are sorted before processing to prevent deadlocks when two users
    try to reserve the same set of seats in different order.

    If any seat fails, the entire transaction is rolled back.
    """
    ordered_seat_ids = sorted(set(seat_ids))
    reserved: List[Dict] = []
    p = get_pool()
    conn = p.getconn(timeout=POOL_ACQUIRE_TIMEOUT)

    try:
        with conn.cursor() as cur:
            for seat_id in ordered_seat_ids:
                # Happy path: attempt reservation directly, no prior SELECT
                cur.execute(
                    """
                    UPDATE seats
                    SET status                  = 'reserved',
                        reserved_by             = %s,
                        reserved_at             = NOW(),
                        reservation_expires_at  = NOW() + (%s * INTERVAL '1 minute')
                    WHERE id = %s AND concert_id = %s AND status = 'available'
                    RETURNING id, section, row_label, seat_number, price
                    """,
                    (user_id, RESERVATION_TIMEOUT_MINUTES, seat_id, concert_id),
                )
                result = cur.fetchone()

                if result is not None:
                    reserved.append(dict(result))
                    continue

                # Failure path: rollback and SELECT only to build the error message
                conn.rollback()
                cur.execute(
                    """
                    SELECT s.section, s.row_label, s.seat_number, c.name AS concert_name
                    FROM seats s
                    JOIN concerts c ON c.id = s.concert_id
                    WHERE s.id = %s AND s.concert_id = %s
                    """,
                    (seat_id, concert_id),
                )
                info = cur.fetchone()

                if info is None:
                    return False, f"Asiento ID {seat_id} no encontrado en este recital.", []

                label = (
                    f"{info['section'].upper()} "
                    f"{info['row_label']}{info['seat_number']}"
                )
                race_logger.log_race_condition(
                    seat_id=seat_id,
                    seat_label=label,
                    concert_name=info["concert_name"],
                    loser_username=username,
                    loser_user_id=user_id,
                )
                return (
                    False,
                    f"El asiento {label} ya fue seleccionado por otro usuario.",
                    [],
                )

        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        p.putconn(conn)

    # Broadcast real-time update to all WebSocket clients watching this concert
    for seat in reserved:
        ws_manager.broadcast_from_thread(
            concert_id,
            {
                "type": "seat_reserved",
                "seat_id": seat["id"],
                "status": "reserved",
                "reserved_by": user_id,
            },
        )

    return True, "Asientos reservados exitosamente.", reserved


def release_seats(
    seat_ids: List[int], user_id: int, concert_id: int
) -> Tuple[bool, str]:
    with get_cursor() as (cur, conn):
        cur.execute(
            """
            UPDATE seats
            SET status                  = 'available',
                reserved_by             = NULL,
                reserved_at             = NULL,
                reservation_expires_at  = NULL
            WHERE id = ANY(%s) AND reserved_by = %s AND status = 'reserved'
            RETURNING id
            """,
            (seat_ids, user_id),
        )
        released_ids = [row["id"] for row in cur.fetchall()]

    for seat_id in released_ids:
        ws_manager.broadcast_from_thread(
            concert_id,
            {"type": "seat_released", "seat_id": seat_id, "status": "available"},
        )

    return True, f"{len(released_ids)} asiento(s) liberado(s)."


def confirm_purchase(
    seat_ids: List[int], user_id: int, concert_id: int
) -> Tuple[bool, str]:
    requested_count = len(set(seat_ids))
    p = get_pool()
    conn = p.getconn(timeout=POOL_ACQUIRE_TIMEOUT)

    try:
        with conn.cursor() as cur:
            cur.execute(
                """
                UPDATE seats
                SET status                  = 'sold',
                    sold_at                 = NOW(),
                    sold_to                 = %s,
                    reserved_by             = NULL,
                    reserved_at             = NULL,
                    reservation_expires_at  = NULL
                WHERE id = ANY(%s)
                  AND concert_id = %s
                  AND reserved_by = %s
                  AND status = 'reserved'
                  AND reservation_expires_at >= NOW()
                RETURNING id, section, row_label, seat_number, price
                """,
                (user_id, seat_ids, concert_id, user_id),
            )
            sold = [dict(row) for row in cur.fetchall()]

        if len(sold) != requested_count:
            conn.rollback()
            return (
                False,
                "Algunos asientos no pudieron confirmarse (reserva expirada o invalida).",
            )

        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        p.putconn(conn)

    total = sum(float(s["price"]) for s in sold)

    for seat in sold:
        ws_manager.broadcast_from_thread(
            concert_id,
            {"type": "seat_sold", "seat_id": seat["id"], "status": "sold"},
        )

    return True, f"Compra confirmada. {len(sold)} entrada(s). Total: ${total:.2f}"


def get_user_reserved_seats(user_id: int, concert_id: int) -> List[Dict]:
    with get_cursor(commit=False) as (cur, conn):
        cur.execute(
            """
            SELECT id, section, row_label, seat_number, price,
                   reservation_expires_at, status
            FROM seats
            WHERE reserved_by = %s AND concert_id = %s AND status = 'reserved'
            """,
            (user_id, concert_id),
        )
        return [dict(row) for row in cur.fetchall()]
