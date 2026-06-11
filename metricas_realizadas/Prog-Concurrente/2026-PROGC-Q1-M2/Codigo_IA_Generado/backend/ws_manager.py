import asyncio
import threading
from typing import Dict, Set
from fastapi import WebSocket


class ConnectionManager:
    """
    Manages WebSocket connections per concert.
    Uses threading.Lock (OS-level primitive) for thread-safe access to the
    connections dictionary, since broadcasts can be triggered from worker threads.
    """

    def __init__(self):
        # OS-level lock protecting the connections dict
        self._lock = threading.Lock()
        self._connections: Dict[int, Set[WebSocket]] = {}
        self._loop: asyncio.AbstractEventLoop = None

    def set_event_loop(self, loop: asyncio.AbstractEventLoop):
        self._loop = loop

    async def connect(self, concert_id: int, websocket: WebSocket):
        await websocket.accept()
        with self._lock:
            if concert_id not in self._connections:
                self._connections[concert_id] = set()
            self._connections[concert_id].add(websocket)

    def disconnect(self, concert_id: int, websocket: WebSocket):
        with self._lock:
            if concert_id in self._connections:
                self._connections[concert_id].discard(websocket)

    async def broadcast_to_concert(self, concert_id: int, message: dict):
        with self._lock:
            connections = list(self._connections.get(concert_id, set()))

        dead = []
        for websocket in connections:
            try:
                await websocket.send_json(message)
            except Exception:
                dead.append(websocket)

        if dead:
            with self._lock:
                for ws in dead:
                    self._connections.get(concert_id, set()).discard(ws)

    def broadcast_from_thread(self, concert_id: int, message: dict):
        """
        Thread-safe bridge: schedules an async broadcast onto the event loop.
        Called from worker threads (HTTP handlers, cleaner thread).
        Uses asyncio.run_coroutine_threadsafe for thread-to-async coordination.
        """
        if self._loop and not self._loop.is_closed():
            asyncio.run_coroutine_threadsafe(
                self.broadcast_to_concert(concert_id, message),
                self._loop,
            )

    def get_connection_count(self, concert_id: int) -> int:
        with self._lock:
            return len(self._connections.get(concert_id, set()))


manager = ConnectionManager()
