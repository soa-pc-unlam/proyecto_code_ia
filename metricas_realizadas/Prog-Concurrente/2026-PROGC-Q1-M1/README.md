# Concurrent Stock Broker — Simulador de CEDEARs

**Actividad Práctica Obligatoria N°1 — Programación Concurrente**  
Año: 2026 | 1° Cuatrimestre

---

## Descripción

Simulador de un broker de acciones concurrente desarrollado en **Java 11+**, que modela el funcionamiento de un mercado de valores con múltiples traders operando en simultáneo sobre activos tipo CEDEAR (AAPL, GOOGL, TSLA, AMZN).

El sistema implementa un **motor de matching** que empareja órdenes de compra y venta en tiempo real, aplicando mecanismos de concurrencia para garantizar consistencia, ausencia de race conditions y correctitud en la sincronización.

---

## Arquitectura del sistema

```
┌─────────────────────────────────────────────────────┐
│                      Broker                         │
│                                                     │
│   ┌──────────┐   ┌──────────┐   ┌──────────┐        │
│   │ Trader 1 │   │ Trader 2 │   │ Trader N │        │
│   └────┬─────┘   └────┬─────┘   └────┬─────┘        │
│        │              │              │              │
│        └──────────────┴──────────────┘              │
│                       │ submitOrder()               │
│                       ▼                             │
│              ┌─────────────────┐                    │
│              │  ConcurrentHashMap (Assets)  │       │
│              └────────┬────────┘                    │
│         ┌─────────────┼─────────────┐               │
│         ▼             ▼             ▼               │
│     Asset(AAPL)  Asset(GOOGL)  Asset(TSLA) ...      │
│     BlockingQueue BlockingQueue BlockingQueue       │
│     ReentrantLock ReentrantLock ReentrantLock       │
│         │             │             │               │
│         ▼             ▼             ▼               │
│    MatchingEngine MatchingEngine MatchingEngine     │
│    (Thread/activo)(Thread/activo)(Thread/activo)    │
└─────────────────────────────────────────────────────┘
```

---

## Estructura del proyecto

```
proyecto/
└── src/
    └── broker/
        ├── Main.java            # Punto de entrada y configuración de la simulación
        ├── Broker.java          # Coordinador central del sistema
        ├── Asset.java           # Representa un activo (CEDEAR) con su libro de órdenes
        ├── MatchingEngine.java  # Motor de matching (un thread por activo)
        ├── Trader.java          # Trader concurrente (productor de órdenes)
        ├── Order.java           # Modelo de datos de una orden de compra/venta
        └── Transaction.java     # Modelo de datos de una transacción ejecutada
```

---

## Mecanismos de concurrencia utilizados

| Mecanismo | Clase | Propósito |
|-----------|-------|-----------|
| `BlockingQueue<Order>` | `Asset` | Cola thread-safe de órdenes entrantes. Bloquea al consumidor si está vacía, evitando busy-waiting |
| `ReentrantLock` (fair) | `Asset` | Protege el libro de órdenes (buyBook/sellBook) e historial de transacciones contra accesos concurrentes |
| `ExecutorService` | `Broker` | Pool de threads fijo para los traders (productores) |
| `ConcurrentHashMap` | `Broker` | Registro de activos con lecturas concurrentes sin locks adicionales |
| `Thread` dedicado | `MatchingEngine` | Un thread por activo para procesar su cola independientemente |
| `volatile boolean` | `Trader`, `MatchingEngine` | Flag de parada visible entre threads sin necesidad de sincronización |

---

## Clases y responsabilidades

### `Order`
Objeto de datos inmutable que representa una orden de mercado. Contiene: ID único, trader emisor, activo, tipo (BUY/SELL), cantidad y precio.

### `Transaction`
Registro de un match ejecutado entre una orden compradora y una vendedora. Almacena ambos IDs de orden, cantidad ejecutada y precio de ejecución (promedio entre oferta y demanda).

### `Asset`
Núcleo del sistema concurrente. Mantiene:
- `BlockingQueue` para recibir órdenes de múltiples traders simultáneamente
- Libro de órdenes (buyBook ordenado por precio desc, sellBook por precio asc)
- Historial de transacciones ejecutadas
- `ReentrantLock` fair para acceso seguro al libro

### `MatchingEngine`
Thread dedicado por activo. Ciclo: toma orden de la `BlockingQueue` → agrega al libro → intenta match → registra transacción si corresponde.

### `Trader`
Productor concurrente. Genera órdenes aleatorias (activo, tipo, cantidad 1-20, precio $90-$110) con intervalos de 200ms a 1200ms entre envíos.

### `Broker`
Coordinador central. Registra activos, inicia un `MatchingEngine` por cada uno y gestiona el pool de traders. Rutea órdenes al `Asset` correcto.

### `Main`
Configura y lanza la simulación: 5 traders, 4 activos, 30 segundos de operación. Imprime resumen final con transacciones y volumen por activo.

---

## Requisitos

- Java 11 o superior
- No requiere dependencias externas ni frameworks adicionales

Verificar versión de Java instalada:
```bash
java --version
```

---

## Cómo ejecutar

### Opción A — Visual Studio Code

1. Instalar la extensión **Extension Pack for Java** de Microsoft
2. Abrir la carpeta del proyecto en VS Code
3. Abrir `Main.java`
4. Hacer clic en **▶ Run** sobre el método `main`

### Opción B — Terminal

```bash
# Desde la raíz del proyecto

# 1. Compilar
javac -d out src/broker/*.java

# 2. Ejecutar
java -cp out broker.Main
```

---

## Salida esperada

Durante los 30 segundos de simulación se imprime en tiempo real la actividad de traders y el motor de matching:

```
[Trader T-001] Submitted [3A79D9F1] T-001 | AAPL | SELL x19 @ $97.79
[MatchingEngine/AAPL] Received [3A79D9F1] T-001 | AAPL | SELL x19 @ $97.79
[MatchingEngine/AAPL] ✔ MATCH [23:43:08.152] BUY:7EBE69AE <-> SELL:3A79D9F1 | AAPL x5 @ $98.04
```

Al finalizar se imprime el resumen por activo:

```
===========================================
           SIMULATION SUMMARY
===========================================
AAPL   | Transactions:  19 | Volume: $12.787,13 | Pending BUY: 7 | Pending SELL: 3
GOOGL  | Transactions:  14 | Volume: $10.117,80 | Pending BUY: 2 | Pending SELL: 10
TSLA   | Transactions:  12 | Volume:  $7.158,84 | Pending BUY: 8 | Pending SELL: 12
AMZN   | Transactions:  19 | Volume: $12.043,76 | Pending BUY: 9 | Pending SELL: 6
-------------------------------------------
TOTAL matched transactions: 64
===========================================
```

---

## Lógica de matching

Una transacción se ejecuta cuando:

```
precio_mejor_BUY >= precio_mejor_SELL
```

El precio de ejecución es el promedio entre ambos:

```
precio_ejecucion = (precio_compra + precio_venta) / 2
```

Las órdenes sin contraparte compatible quedan en el libro y se muestran como `Pending` en el resumen final.

---

## Garantías de correctitud concurrente

- **Race conditions**: evitadas mediante `ReentrantLock` en todo acceso al libro de órdenes
- **Deadlocks**: imposibles por diseño — cada `MatchingEngine` adquiere únicamente el lock de su propio activo, sin dependencias cruzadas
- **Inanición (starvation)**: mitigada con `ReentrantLock(true)` (modo fair), que atiende los threads en orden de llegada
- **Busy-waiting**: eliminado mediante `BlockingQueue.take()`, que suspende el thread hasta que haya trabajo disponible

---

## Herramienta de generación

Código generado mediante **Claude Sonnet 4.6** (Anthropic) como herramienta de VibeCoding, en el contexto de la Actividad Práctica Obligatoria N°1 de Programación Concurrente.

---

## Autores

| Nombre              |   DNI    |
|---------------------|----------|
| Nicole Ocampo       | 44451238 |
| Teo Francis Turri   | 42819058 |
| Lautaro da Silva    | 42816815 |
|                     |          |
