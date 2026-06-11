import threading
import logging
from database import get_pool
from ws_manager import manager as ws_manager
from config import CLEANUP_INTERVAL_SECONDS, POOL_ACQUIRE_TIMEOUT

logger = logging.getLogger(__name__)


class ReservationCleaner(threading.Thread):
    """
    Daemon thread that periodically frees expired temporary seat reservations.

    OS-level concurrency concepts used:
    - threading.Thread: an OS-managed thread running independently of the web server
    - threading.Event: used as a cancellable sleep (stop signal + timeout)
    - psycopg ConnectionPool: each call gets its own DB connection safely

    The cleanup query is itself atomic: it only touches seats where the expiry
    timestamp has passed, so it cannot conflict with the reservation UPDATE in
    seat_service.py (PostgreSQL row-level locking prevents that).
    """

    def __init__(self):
        super().__init__(name="ReservationCleaner", daemon=True)
        self._stop_event = threading.Event()

    def run(self):
        logger.info(
            f"[{self.name}] Iniciado — revisando cada {CLEANUP_INTERVAL_SECONDS}s"
        )
        while not self._stop_event.is_set():
            try:
                self._cleanup()
            except Exception as e:
                logger.error(f"[{self.name}] Error en cleanup: {e}")
            # Waits for the interval OR wakes immediately if stop() is called
            self._stop_event.wait(timeout=CLEANUP_INTERVAL_SECONDS)
        logger.info(f"[{self.name}] Detenido.")

    def _cleanup(self):
        p = get_pool()
        conn = p.getconn(timeout=POOL_ACQUIRE_TIMEOUT)
        try:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    UPDATE seats
                    SET status                  = 'available',
                        reserved_by             = NULL,
                        reserved_at             = NULL,
                        reservation_expires_at  = NULL
                    WHERE status = 'reserved' AND reservation_expires_at < NOW()
                    RETURNING id, concert_id, section, row_label, seat_number
                    """
                )
                released = [dict(row) for row in cur.fetchall()]
            conn.commit()
        except Exception:
            conn.rollback()
            raise
        finally:
            p.putconn(conn)

        if released:
            logger.info(
                f"[{self.name}] {len(released)} reserva(s) expirada(s) liberada(s)."
            )
            for seat in released:
                ws_manager.broadcast_from_thread(
                    seat["concert_id"],
                    {"type": "seat_released", "seat_id": seat["id"], "status": "available"},
                )

    def stop(self):
        self._stop_event.set()


_cleaner: ReservationCleaner = None


def start_cleaner():
    global _cleaner
    _cleaner = ReservationCleaner()
    _cleaner.start()


def stop_cleaner():
    global _cleaner
    if _cleaner:
        _cleaner.stop()
        _cleaner.join(timeout=5)
