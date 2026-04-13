# DeathRun Game

Juego multijugador desarrollado en Godot 4.5.1.

## Autores

* Ghiano, Gonzalo Agustín
* Minaudo, Lucas Agustín
* Polito, Thiago Martín
* Gomez, Matías Alejandro
* De Luca, Leonel Maximiliano

## Requisitos del Sistema

- **Sistema Operativo:** Windows 10/11 (64 bits)
- **Arquitectura:** x86_64
- **Red:** Conexión LAN para modo multijugador en red local

## Instalación

1. Descarga el archivo `.exe` del juego
2. Coloca el ejecutable en la carpeta de tu preferencia
3. No requiere instalación adicional

## Cómo Jugar

El juego requiere dos instancias para funcionar: un **Host** (servidor) y un **Cliente**.

### Opción 1: Dos jugadores en la misma computadora

Útil para pruebas o jugar localmente.

1. Ejecuta el `.exe` dos veces (se abrirán dos ventanas del juego)
2. En la primera ventana, crea el **Host**
3. En la segunda ventana, conéctate como **Cliente** usando la dirección:
   ```
   127.0.0.1
   ```
   > Esta es la dirección de loopback, que apunta a tu propia máquina.

### Opción 2: Dos jugadores en la misma red LAN

Para jugar en computadoras diferentes conectadas a la misma red.

#### En la PC que será el Host:

1. Ejecuta el `.exe`
2. Crea el **Host**
3. Obtén tu dirección IPv4 privada (ver instrucciones abajo)
4. Comparte esta dirección con el otro jugador

#### En la PC que será el Cliente:

1. Ejecuta el `.exe`
2. Conéctate como **Cliente**
3. Ingresa la dirección IPv4 privada del Host

## Cómo obtener tu dirección IPv4 privada (Windows)

1. Abre el **Símbolo del sistema** (CMD):
   - Presiona `Win + R`
   - Escribe `cmd` y presiona Enter

2. Ejecuta el comando:
   ```
   ipconfig
   ```

3. Busca el adaptador de red activo (generalmente "Adaptador de Ethernet" o "Adaptador de LAN inalámbrica Wi-Fi")

4. Copia el valor de **Dirección IPv4**, que tendrá un formato similar a:
   ```
   192.168.x.x
   ```
   o
   ```
   10.x.x.x
   ```

## Información Técnica

- **Motor:** Godot 4.5.1
- **Plataforma:** Windows Desktop
- **Arquitectura:** x86_64
- **Protocolo de red:** ENet (UDP)