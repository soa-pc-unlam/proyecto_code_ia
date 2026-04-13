# Descripción del Proyecto

Este proyecto consiste en una recreación del clásico juego Bomberman adaptado a un entorno multijugador en red local (LAN). El objetivo es demostrar la aplicación de conceptos de programación concurrente, sincronización de procesos y comunicación en redes mediante una arquitectura Cliente-Servidor Autoritaria.

## Finalidad

La solución permite que hasta 4 jugadores se conecten simultáneamente en una partida donde deben colocar bombas, destruir muros y eliminar a los oponentes. El software gestiona la concurrencia de las acciones de los jugadores, asegurando la consistencia del estado del juego en todas las máquinas conectadas.

---

## Stack Tecnológico

* **Motor Gráfico:** Godot Engine 4.x.
* **Lenguaje de Programación:** GDScript.
* **API de Red:** High-Level Multiplayer API de Godot (sobre ENet).
* **Protocolo de Transporte:** UDP (Reliable/Unreliable) abstraído mediante RPCs.

---

## Arquitectura de Software

Se implementó un modelo de **Servidor Autoritativo**:

1.  **Host (Servidor):** Es la autoridad única. Gestiona el estado del mundo, valida movimientos, instancia bombas, calcula daños y decide las condiciones de victoria.
2.  **Peers (Clientes):** Envían inputs y solicitudes (RPCs) al servidor y reciben actualizaciones de estado mediante `MultiplayerSynchronizer` y `MultiplayerSpawner`.

### Requisitos del Sistema

* **Sistema Operativo:** Windows 10/11, Linux o macOS.
* **Hardware:** CPU Dual Core o superior, 4GB RAM, Gráficos integrados compatibles con OpenGL 3.3 / Vulkan.
* **Red:** Conexión LAN (Ethernet o Wi-Fi) o Localhost para pruebas.

---

## Arquitectura de Concurrencia y Sincronización

### 1. Modelo de Procesos (Distributed Processes)

La solución no utiliza multihilo explícito (*multithreading*) dentro de la lógica del juego para gestionar a los jugadores. En su lugar, se implementa una arquitectura de **Sistemas Distribuidos**:

* Cada instancia del juego (ejecutable) funciona como un Proceso independiente del Sistema Operativo.
* Estos procesos se ejecutan de manera concurrente (y potencialmente paralela si están en distintas máquinas físicas).
* Existe un Proceso Servidor (Host) que actúa como la autoridad central y "N" Procesos Cliente (Peers) que actúan como terminales de entrada/salida.

#### Clasificación según la Taxonomía de Flynn
Desde el punto de vista de la arquitectura de computadoras, la solución implementada se clasifica como un sistema **MIMD (Multiple Instruction, Multiple Data) de Memoria Distribuida (Multicomputadora)**.

* **Justificación:** El sistema está compuesto por múltiples nodos de procesamiento (las PCs de los jugadores), donde cada nodo ejecuta su propio flujo de instrucciones de manera asíncrona y opera sobre su propio espacio de direcciones de memoria local.
* **Implicancia en el Diseño:** Debido a que se trata de una arquitectura de memoria distribuida (no compartida), no es posible utilizar mecanismos de sincronización de bajo nivel como semáforos o monitores compartidos. Por lo tanto, la sincronización y comunicación se resuelven mediante el modelo de **Paso de Mensajes (Message Passing)**, implementado a través de RPCs.

### 2. Comunicación y Paso de Mensajes (RPCs)

Dado que los procesos no comparten memoria física, la comunicación se resuelve mediante RPCs (Remote Procedure Calls) sobre el protocolo UDP (ENet).

* **Solicitudes (Cliente -> Servidor):** Los clientes no modifican el estado del juego directamente. Envían un "mensaje" (RPC) solicitando una acción.
* *Ejemplo:* Cuando el Jugador 2 presiona "Poner Bomba", envía `rpc_id(1, "request_bomb_spawn")` al Servidor.
* **Difusión (Servidor -> Clientes):** El servidor procesa los mensajes de forma secuencial en su bucle de física, garantizando la atomicidad, y luego difunde el resultado a los clientes mediante replicación.

#### Bucle de Física del Servidor (Metrónomo)
1.  **Espera:** Acumula todos los mensajes (RPCs) que llegan de la red durante $1/60$ de segundo.
2.  **Tick (Inicio del Bucle):** Toma los mensajes en orden FIFO, ejecuta la lógica (movimiento, bombas) y calcula colisiones.
3.  **Replicación:** Una vez calculado el estado real global, lo envía a los clientes.

### 3. Sincronización de Estado

Para mantener la coherencia visual entre los procesos, se utilizan dos estrategias:

* **Sincronización Continua (MultiplayerSynchronizer):** Para variables de alta frecuencia como `position` y `animation`. El servidor escribe la variable y el motor propaga el cambio automáticamente en cada tick de red.
**Sincronización de Eventos (MultiplayerSpawner):** Garantiza que la creación y destrucción de nodos (Bombas, Explosiones, Muros) ocurra en todos los procesos simultáneamente para mantener el árbol de escenas idéntico.

### 4. Control de Concurrencia y Condiciones de Carrera

Para evitar condiciones de carrera (ej: dos jugadores agarrando el mismo PowerUp), se utiliza el modelo de **Autoridad del Servidor**:

* **Exclusión Mutua Implícita:** El servidor procesa los paquetes de red de manera secuencial en su hilo principal (Main Thread).
* **Atomicidad:** La lógica de colisión ocurre solo en el Servidor. Si dos mensajes llegan "al mismo tiempo", el servidor procesa uno primero y elimina el objeto; cuando procese el segundo mensaje, el objeto ya no existirá, evitando estados inconsistente.

---

## Patrones de Diseño Utilizados


| Patrón de Diseño | Implementación en el Proyecto | Finalidad |
| :---- | :---- | :---- |
| **Observer** | Señales (`timeout`, `peer_connected`, `animation_finished`). | Reaccionar a eventos de forma asíncrona y desacoplada. |
| **Factory Method** | Instanciación de escenas en `main.gd` (Jugadores) y explosiones. | Creación dinámica de objetos en tiempo de ejecución. |
| **Command (RPC)** | Llamadas `rpc_id` y `rpc` para acciones de red. | Solicitar ejecución de lógica en procesos remotos (Servidor). |
| **Singleton** | Clase estática `Utils` y objeto `multiplayer`. | Acceso global a constantes y gestión de conexión. |
