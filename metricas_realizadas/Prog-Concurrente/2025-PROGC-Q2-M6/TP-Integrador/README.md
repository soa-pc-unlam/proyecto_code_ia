# ChessGame

Juego de ajedrez multiplayer con arquitectura cliente-servidor desarrollado para la materia **Programación Concurrente** de la Universidad Nacional de La Matanza.

El proyecto implementa un sistema completo de ajedrez en tiempo real donde múltiples jugadores pueden crear y unirse a partidas simultáneas, con validación de reglas en el servidor y una interfaz gráfica reactiva en el cliente.

## Integrantes - Grupo M6

- Novoa Romero, Martin Ricardo
- La Giglia, Rodrigo Ariel
- Gonzalez, Agustin Elias

## Arquitectura

El proyecto está dividido en dos componentes principales:

### Servidor (Kotlin + Jetty)
Servidor WebSocket que gestiona la lógica de negocio, validación de movimientos mediante motor de reglas, y broadcasting de estado a todos los participantes. Diseñado para manejar múltiples conexiones concurrentes de forma eficiente utilizando estructuras thread-safe.

**Características:**
- Pool de threads escalable (8-200) gestionado por Jetty
- Validación de movimientos mediante `chesslib`
- Limpieza automática de partidas inactivas
- Soporte para jugadores y espectadores

### Cliente (Godot 4.5)
Interfaz gráfica desarrollada en Godot con arquitectura reactiva basada en señales. Utiliza singletons autoload para centralizar networking y estado global, minimizando acoplamiento entre escenas.

**Características:**
- Conexión WebSocket en tiempo real
- Sistema de señales para actualizaciones de UI
- Validación local de movimientos pre-envío
- Exportable a Web (WASM) y Windows

## Documentación

Para información detallada sobre cada componente:

- **[Servidor](server/README.md)**
- **[Cliente](client/README.md)**

## Tech Stack

| Componente   | Tecnologías                                               |
|--------------|-----------------------------------------------------------|
| Servidor     | Kotlin 2.2.0, Jetty 11, Jackson, chesslib, GraalVM Native |
| Cliente      | Godot 4.5, GDScript, WebSocket, HTML5/WASM                |
| Comunicación | WebSocket (JSON)                                          |

## Quick Start

### 1. Iniciar Servidor
```cmd
cd server
gradlew.bat run
```
El servidor estará disponible en `ws://localhost:3000/ws`

### 2. Ejecutar Cliente
```
1. Abrir Godot 4.5+
2. Cargar client/project.godot
3. Presionar F5
```
