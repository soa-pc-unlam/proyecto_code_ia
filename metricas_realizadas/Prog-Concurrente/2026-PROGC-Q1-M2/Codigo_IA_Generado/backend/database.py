import threading
from contextlib import contextmanager
from psycopg_pool import ConnectionPool
from psycopg.rows import dict_row
from config import (DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD,
                    DB_MIN_CONNECTIONS, DB_MAX_CONNECTIONS, POOL_ACQUIRE_TIMEOUT)

# Thread-safe connection pool (psycopg 3.x handles thread synchronization internally)
_pool = None
_pool_lock = threading.Lock()


def init_pool():
    global _pool
    with _pool_lock:
        if _pool is None:
            conninfo = f"host={DB_HOST} port={DB_PORT} dbname={DB_NAME} user={DB_USER} password={DB_PASSWORD} client_encoding=UTF8"
            _pool = ConnectionPool(
                conninfo,
                min_size=DB_MIN_CONNECTIONS,
                max_size=DB_MAX_CONNECTIONS,
                kwargs={"row_factory": dict_row},
            )


def get_pool():
    if _pool is None:
        init_pool()
    return _pool


@contextmanager
def get_connection():
    """Context manager that yields a raw DB connection from the pool."""
    p = get_pool()
    conn = p.getconn(timeout=POOL_ACQUIRE_TIMEOUT)
    try:
        yield conn
    finally:
        p.putconn(conn)


@contextmanager
def get_cursor(commit: bool = True):
    """Context manager that yields (cursor, conn). Auto-commits or rolls back."""
    p = get_pool()
    conn = p.getconn(timeout=POOL_ACQUIRE_TIMEOUT)
    try:
        with conn.cursor() as cur:
            yield cur, conn
            if commit:
                conn.commit()
            else:
                conn.rollback()
    except Exception:
        conn.rollback()
        raise
    finally:
        p.putconn(conn)


def close_pool():
    global _pool
    if _pool:
        _pool.close()
        _pool = None
