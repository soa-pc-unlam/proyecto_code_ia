# Prompt técnico utilizado

**Herramienta:** Claude (Claude Code) · **Modelo:** Sonnet · **Lenguaje:** Java 21 · **Metodología:** prompt técnico (prompt estructurado + 2 refinamientos)

---

Actuá como ingeniero de software senior especializado en programación concurrente.
Generá un programa **completo, compilable y ejecutable en Java 21 (solo librería estándar, sin dependencias externas)** que simule un **banco con transferencias concurrentes entre cuentas**.

## 1. Objetivo funcional
- El banco tiene N cuentas (por defecto 50), cada una con saldo inicial configurable (por defecto 10.000,00). Los montos se manejan en centavos con `long`.
- Varios hilos **productores** generan solicitudes de transferencia aleatorias (cuenta origen, cuenta destino, monto) y las colocan en una **cola acotada**.
- Varios hilos **trabajadores (consumidores)** toman solicitudes de la cola y ejecutan la transferencia.
- Una transferencia se rechaza si origen == destino o si el saldo de origen es insuficiente. Nunca puede quedar un saldo negativo.
- Un hilo **auditor** verifica periódicamente que la suma de todos los saldos sea igual a la suma inicial (invariante de conservación del dinero) tomando una **foto consistente** del banco.
- Un hilo **monitor** imprime cada segundo un panel por consola: transferencias procesadas, aceptadas, rechazadas, throughput, tamaño de la cola, resultado de la última auditoría.
- Al finalizar se imprime un informe: tiempo total, throughput, transferencias por trabajador, suma final vs. inicial y veredicto OK/FALLA (código de salida 0/1).

## 2. Requisitos de concurrencia
- Sincronización con `ReentrantLock` por cuenta (no usar un lock global para las transferencias).
- **Prevención de deadlock**: adquirir siempre los locks en orden creciente de id de cuenta. El auditor debe respetar el mismo orden.
- Sin condiciones de carrera: todo acceso al saldo debe estar protegido; contadores globales con `AtomicLong`.
- Productores-consumidores con `ArrayBlockingQueue`; terminación limpia con *poison pill* (sin `Thread.stop`, sin busy-waiting).
- Liberar los locks siempre en `finally`.
- Cierre ordenado: esperar (`join`) a todos los hilos antes del informe final.

## 3. Interfaz y parámetros
Parámetros por línea de comandos (todos opcionales): `--cuentas`, `--saldo`, `--productores`, `--workers`, `--transferencias`, `--cola`, `--auditMs`, `--seed`, `--quiet`.
Agregar `--unsafe`, un modo **deliberadamente sin sincronización**, solo para demostrar la condición de carrera y validar que el auditor la detecta.
El panel por consola debe ser claro y legible.

## 4. Entregable
Un único archivo `BancoConcurrente.java` comentado, más las instrucciones de compilación y ejecución.

---

## Refinamiento 1 (v1): corrección de codificación
Tras la primera ejecución se corrigió la codificación de la consola (UTF-8) porque las tildes del informe final se veían mal. Es el único cambio hecho sobre el código inicial; las opciones `--quiet`, `--unsafe`, el auditor con foto consistente y el cierre con poison pill ya estaban en el prompt original.

---

## Refinamiento 2 (v2): prompt de seguimiento
Instrucción del grupo: *«Realizamos otra corrida y vamos a refinar el código para mejorarlo.»* Las mejoras concretas se eligieron a partir de las debilidades del análisis crítico de la v1 y se pidieron así:

Refinar el código para mejorarlo (v2), atacando las debilidades del análisis crítico de la v1:
1. Validar los parámetros con mensajes claros y código de salida 2; separar run() de main (sin System.exit dentro de la lógica) para poder probarlo.
2. Evitar falso compartido en los contadores por worker (relleno de 16 longs).
3. Agregar un watchdog con ThreadMXBean que detecte deadlocks reales en ejecución.
4. Medir la pausa del auditor (media y máxima).
5. Agregar opciones --queue (array|linked) y --fair para comparar cola y justicia de locks.
6. Agregar pruebas automáticas sin dependencias externas (conservación, estrés, control unsafe, validación).

Resultado: `codigo/v2/BancoConcurrente.java` y `codigo/v2/PruebasBanco.java` (la v1 se conserva en `codigo/v1/` para comparar).
