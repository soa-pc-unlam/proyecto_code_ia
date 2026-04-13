# Batalla Naval

Un juego de Batalla Naval implementado en Python con soporte multijugador en tiempo real utilizando sockets asíncronos.

## Requisitos del Sistema

- Python 3.7 o superior
- Pygame
- Sistema operativo Windows

## Instalación y Ejecución Local (Para jugar en la misma PC)

### Paso 1: Clonar el Repositorio

```bash
git clone <URL_DEL_REPOSITORIO>
cd 2025-PROGC-Q2-M3/TP-Integrador
```

### Paso 2: Iniciar el Servidor

1. Navega hasta la carpeta del servidor:
   ```bash
   cd server
   ```

2. Ejecuta el servidor utilizando el archivo batch:
   ```bash
   run_server.bat
   ```

El servidor se iniciará en `localhost:8888` y estará esperando conexiones de los jugadores.

### Paso 3: Ejecutar el Juego

1. Abre una nueva terminal y navega hasta la carpeta del juego:
   ```bash
   cd game
   ```

2. Ejecuta el cliente del juego:
   ```bash
   BatallaNaval.bat
   ```

3. El juego se abrirá y se conectará automáticamente al servidor local.

### Paso 4: Jugar

1. Unicamente debe existir un servidor corriendo
2. Necesitas dos instancias del juego ejecutándose para poder jugar (dos ventanas del juego)
3. Una vez que ambos jugadores estén conectados, pueden hacer clic en "Iniciar Juego"
4. Coloca tus barcos en el tablero
5. Cuando ambos jugadores hayan terminado de colocar sus barcos, comenzará la fase de batalla
6. Haz clic en el tablero enemigo para disparar durante tu turno