# Arquitectura del Sistema de Concurrencia

## 📊 Visión General

El Sistema de Venta de Entradas implementa un modelo robusto de manejo de concurrencia utilizando primitivas del sistema operativo y características atómicas de la base de datos.

```
┌─────────────────────────────────────────────────────────────┐
│                     FRONTEND (Browser)                       │
│  - WebSocket connections                                    │
│  - Real-time seat updates                                   │
│  - JWT authentication tokens                                │
└────────────┬────────────────────────────────┬────────────────┘
             │ HTTP/WebSocket                 │
             ▼                                 ▼
┌─────────────────────────────────────────────────────────────┐
│                  BACKEND (FastAPI + Uvicorn)                │
│  ┌─────────────────────────────────────────────────────────┐│
│  │  HTTP Handlers (seat reservation, payment, etc.)        ││
│  │  WebSocket Manager (with threading.Lock)                ││
│  │  Authentication (JWT)                                   ││
│  │  Concert/Seat Service                                   ││
│  └─────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────┐│
│  │  ReservationCleaner Thread (daemon)                      ││
│  │  - Runs every 30 seconds                                ││
│  │  - Cleans expired reservations                          ││
│  │  - Broadcasts releases via WebSocket                    ││
│  └─────────────────────────────────────────────────────────┘│
└────────────┬────────────────────────────────┬────────────────┘
             │ Atomic SQL queries             │
             ▼                                 ▼
┌─────────────────────────────────────────────────────────────┐
│              PostgreSQL (Connection Pool)                    │
│  - ThreadedConnectionPool (5-20 connections)                │
│  - Row-level locking                                        │
│  - ACID transactions                                        │
│  - Atomic UPDATE with WHERE status='available'              │
└─────────────────────────────────────────────────────────────┘
```

## 🔒 Estrategia de Sincronización

### Nivel 1: Aplicación (Threads)

#### WebSocket Manager
```python
class ConnectionManager:
    def __init__(self):
        self._lock = threading.Lock()  # OS-level primitive
        self._connections: Dict[int, Set[WebSocket]] = {}
```

**Propósito**: Proteger acceso concurrente a la lista de conexiones WebSocket.

**Mecanismo**:
- `threading.Lock`: Primitiva del SO que garantiza exclusión mutua
- Adquirido cuando se modifica `_connections`
- Liberado inmediatamente después de la operación

**Por qué es necesario**: 
- Los HTTP handlers y el ReservationCleaner acceden a `_connections` simultáneamente
- Sin el lock, podrían ocurrir race conditions en la colección de conexiones

#### ReservationCleaner Thread
```python
class ReservationCleaner(threading.Thread):
    def __init__(self):
        super().__init__(name="ReservationCleaner", daemon=True)
        self._stop_event = threading.Event()
    
    def run(self):
        while not self._stop_event.is_set():
            self._cleanup()
            self._stop_event.wait(timeout=30)  # Cancellable sleep
```

**Propósito**: Limpiar automáticamente las reservas expiradas cada 30 segundos.

**Mecanismo**:
- **threading.Thread**: Thread daemon independiente del servidor web
- **threading.Event**: Permite parar el thread de forma limpia
  - `.wait(timeout=30)`: Duerme pero puede ser interrumpido por `.set()`
  - No bloquea el servidor; solo usa CPU cuando se ejecuta

**Operación**:
1. Cada 30 segundos, actualiza asientos con `reservation_expires_at < NOW()`
2. Cambia estado de `reserved` a `available`
3. Emite eventos WebSocket a todos los clientes conectados

### Nivel 2: Pool de Conexiones a BD

#### ThreadedConnectionPool
```python
_pool = pool.ThreadedConnectionPool(
    minconn=5,
    maxconn=20,
    host=DB_HOST,
    port=DB_PORT,
    ...
)
```

**Propósito**: Proporcionar conexiones a BD thread-safe para múltiples handlers HTTP.

**Mecanismo**:
- Mantiene 5-20 conexiones permanentes a PostgreSQL
- psycopg2 sincroniza internamente el acceso (`conn = pool.getconn()`)
- Cada handler obtiene su propia conexión: no hay competencia

**Beneficio**:
- O(1) obtención de conexión en lugar de O(n) creación de conexión
- Maneja cientos de usuarios simultáneos sin conectar/desconectar constantemente

### Nivel 3: Base de Datos (Atomicidad)

#### Reserva Atómica
```sql
UPDATE seats
SET status = 'reserved',
    reserved_by = %s,
    reserved_at = NOW(),
    reservation_expires_at = NOW() + interval '5 minutes'
WHERE id = %s AND concert_id = %s AND status = 'available'
RETURNING id, section, row_label, seat_number, price
```

**Propósito**: Garantizar que solo UN usuario puede reservar cada asiento.

**Mecanismo (Row-Level Locking)**:
1. PostgreSQL adquiere un LOCK exclusivo en la fila
2. Lee el estado actual (`status = 'available'`)
3. Si es verdad, actualiza el asiento
4. Si es falso (ya fue reservado), no modifica nada
5. Libera el lock
6. El cliente recibe el número de filas modificadas

**Garantía ACID**:
- **Atomicidad**: La consulta es indivisible
- **Consistencia**: El estado es válido antes y después
- **Aislamiento**: No ve cambios de transacciones no commiteadas
- **Durabilidad**: Escrito a disco antes de responder al cliente

#### Índices para Alto Rendimiento
```sql
CREATE INDEX idx_seats_concert_status ON seats (concert_id, status);
CREATE INDEX idx_seats_reservation_expires ON seats (reservation_expires_at)
WHERE status = 'reserved';
```

**Propósito**: Acelerar búsquedas de asientos disponibles y expirados.

## 🔄 Flujo de Race Condition Detectada

```
Usuario A                          Usuario B                    Base de Datos
     │                                 │                             │
     ├──────────┐                      │                             │
     │          │ Quiero asiento C1    │                             │
     │          └──────────────────────────────────────────────────►│
     │                                 │                             │
     │                                 ├──────────┐                 │
     │                                 │          │ Quiero asiento  │
     │                                 │          │ C1 (casi igual  │
     │                                 │          │ tiempo)         │
     │                                 │          └────────────────►│
     │                                 │                    ┌────────┤
     │                                 │                    │ SELECT │
     │                                 │                    │ FOR    │
     │                                 │                    │ UPDATE │
     │                      ┌──────────┤◄──────────────────┘        │
     │                      │ Ambos    │                             │
     │                      │ quieren  │ ┌───────────────────────────┤
     │                      │ lo mismo │ │ Actualizar A (primero)    │
     │                      │          │ │ rowcount=1  ✓             │
     │                      │          │ │                           │
     │                      │          │ │ B intenta actualizar      │
     │                      │          │ │ rowcount=0  ✗ RACE!       │
     │                      │          └─┼─────────────────────────► │
     │                                 │  │                           │
     │◄──────────────────────────────────┤  rowcount=1               │
     │ ✓ Asiento reservado              │  (éxito)                   │
     │                                 │                             │
     │                                 └────────────────────────────►
     │                                    rowcount=0                 │
     │                                    (RACE CONDITION)           │
     │                                    → Log + Error              │
     ▼                                 ▼                             ▼
```

### Detección y Logging

Cuando `rowcount = 0`, se ejecuta:

```python
race_logger.log_race_condition(
    seat_id=seat_id,
    seat_label="CAMPO A1",
    concert_name="Rock Nacional",
    loser_username="testuser5",
    loser_user_id=7,
)
```

Esto:
1. Escribe a `logs/race_conditions.log` (archivo)
2. Inserta en `race_condition_log` (tabla de BD)
3. Notifica al usuario: "Asiento ya fue seleccionado"

## 🔌 Comunicación en Tiempo Real (WebSocket)

### Flujo de Actualización

```
Usuario A reserva asiento
         │
         ▼
  Backend (seat_service.py)
         │
         ├─► Actualiza BD ✓
         │
         └─► ws_manager.broadcast_from_thread(concert_id, {
                 "type": "seat_reserved",
                 "seat_id": 145,
                 "status": "reserved"
             })
                  │
                  ▼
         asyncio.run_coroutine_threadsafe()
             (cruza HTTP thread → async loop)
                  │
                  ▼
         ConnectionManager.broadcast_to_concert()
             (envía a todos los WebSocket)
                  │
         ┌────────┼────────┬────────┐
         ▼        ▼        ▼        ▼
      Usuario    Usuario Usuario Usuario
         B        C       D       E
    (todos ven el asiento C1 como ROJO)
```

### Sincronización Thread-to-Async

```python
def broadcast_from_thread(self, concert_id: int, message: dict):
    asyncio.run_coroutine_threadsafe(
        self.broadcast_to_concert(concert_id, message),
        self._loop,  # Event loop del servidor
    )
```

**Por qué**: 
- FastAPI usa async/await (asyncio event loop)
- Los handlers HTTP corren en thread pool (threads)
- WebSocket es async
- Necesitamos puente thread-safe entre mundos

## 📈 Escalabilidad

### Números Clave

| Parámetro | Valor | Razón |
|-----------|-------|-------|
| DB Min Connections | 5 | Mínimo para servir de base |
| DB Max Connections | 20 | Suficiente para cientos de usuarios HTTP |
| Cleanup Interval | 30s | Balance entre liberación rápida y carga BD |
| Reservation Timeout | 5m | Tiempo razonable para el usuario seleccionar |
| Purchase Timeout | 10m | Suficiente para completar compra |

### Bajo Carga (100+ usuarios simultáneos)

1. **HTTP Handlers**: Distribuidos entre workers Uvicorn
2. **WebSocket Connections**: Mantenidas en memoria (socket abierto)
3. **DB Queries**: Rápidas y cortas (< 50ms típico)
4. **Thread Cleaner**: No interfiere (corre cada 30s)

## 🛡️ Prevención de Deadlocks

### Estrategia 1: Ordenamiento Consistente
Todas las reservas usan la misma columna de ordenamiento:
```sql
ORDER BY id  -- Siempre en el mismo orden
```

### Estrategia 2: Transacciones Cortas
```python
try:
    with conn.cursor() as cur:
        cur.execute(...)  # Mínimo código
    conn.commit()  # Release locks
except:
    conn.rollback()
```

### Estrategia 3: Sin Locks Persistentes
- Locks de fila se liberan al commit
- No mantenemos locks entre HTTP requests
- Cada request es independiente

### Estrategia 4: Timeout en Reservas
- Reservas expiran después de 5 minutos
- No se quedan "atrapadas" indefinidamente
- Cleaner las libera automáticamente

## 📊 Monitoreo y Debugging

### Logs

```bash
# Logs generales
tail -f logs/*.log

# Solo race conditions
grep RACE_CONDITION logs/race_conditions.log
```

### Estadísticas en BD

```sql
SELECT
    status,
    COUNT(*) as count
FROM seats
WHERE concert_id = 1
GROUP BY status;
```

### API de Monitoreo

```bash
# Ver race conditions detectadas
curl -H "Authorization: Bearer $TOKEN" http://localhost:8000/api/admin/race-conditions
```

## 🧪 Pruebas

Ver `tests/concurrent_test.py` para:
- Prueba de 10 usuarios reservando el mismo asiento
- Prueba secuencial de múltiples usuarios
- Validación de race condition detection

Ejecutar:
```bash
python3 tests/concurrent_test.py
```

## 📚 Referencias de Conceptos

### Concepts Usados del SO

| Concepto | Implementación | Ubicación |
|----------|----------------|-----------|
| **Threads** | `threading.Thread` | `cleaner.py` |
| **Locks** | `threading.Lock` | `ws_manager.py` |
| **Events** | `threading.Event` | `cleaner.py` |
| **Connection Pool** | `ThreadedConnectionPool` | `database.py` |
| **Atomicity** | SQL transactions | `seat_service.py` |
| **Row Locking** | `WHERE status='available'` | SQL |

### Problemas Evitados

| Problema | Causa | Solución |
|----------|-------|----------|
| Race Condition | Dos users mismo asiento | UPDATE atómico |
| Deadlock | Locks mal ordenados | Orden consistente + timeouts |
| Lost Update | Lectura antes de escritura | UPDATE directo (no READ + UPDATE) |
| Busy Waiting | Loop activo | `Event.wait()` + `sleep()` |
| Resource Leak | Conexiones no liberadas | Pool + context managers |

## 🔐 Seguridad

### Inyección SQL
- Todos los valores usan parámetros preparados (`%s`)
- Nunca concatenamos strings en SQL

### Autenticación
- JWT tokens con expiración
- Bearer token en cada request

### Validación
- Pydantic models validación de entrada
- Comprobación de propiedad (user_id matches)

---

**Última actualización**: 2026-05-25  
**Sistema**: Ticket Selling System v1.0.0
