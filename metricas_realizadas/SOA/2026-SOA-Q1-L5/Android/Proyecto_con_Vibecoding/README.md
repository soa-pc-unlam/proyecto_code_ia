# Stock & Seguridad — App Android (SOA)

App Android que se conecta al ESP32 **a través de Node-RED** (endpoints HTTP REST)
para monitorear y controlar el sistema embebido de **Stock** y **Seguridad**.

## ¿Cómo se conecta?

La infraestructura del proyecto expone dos caminos: MQTT (Mosquitto) y HTTP (Node-RED).
Esta app usa los **endpoints HTTP de Node-RED**, porque cubren exactamente lo que se pide
(estado, actualización manual y comandos) con una sola llamada de lectura.

| Acción en la app | Método | Endpoint |
| ---------------- | ------ | -------- |
| Leer todo el estado (health, system, alarma, estantes) | `GET`  | `/api/:device` |
| Activar / desactivar modo Stock     | `POST` | `/api/:device/stock` → `{"status":"ON"\|"OFF"}` |
| Activar / desactivar modo Seguridad | `POST` | `/api/:device/security` → `{"status":"ON"\|"OFF"}` |
| Silenciar / habilitar alarma (buzzer) | `POST` | `/api/:device/security/alarm` → `{"status":"MUTE"\|"UNMUTE"}` |

## Pantallas

1. **Principal**
   - Estado del servicio (conectado / sin conexión) + salud del dispositivo.
   - Botón de **actualización manual** (ícono de refresco).
   - Configuración de conexión: **IP/Host**, **Puerto** (por defecto `1880`) y **Dispositivo** (`corridor-01`).
   - Dos **burbujas**: *Modo Stock* y *Modo Seguridad*, que navegan a sus pantallas.
2. **Stock** — inventario por estante (producto, stock actual vs. mínimo, pesos) con aviso *REPONER*, y switch para activar el modo Stock.
3. **Seguridad** — estado de anomalía por estante (peso actual, referencia y diferencia), switch para activar el modo Seguridad y **control de la alarma (buzzer)**.

## Requisitos

- **Android Studio** (Ladybug / Meerkat o superior).
- **JDK 17** (Android Studio ya incluye uno).
- SDK de Android **34** (Android Studio lo descarga solo al sincronizar).

## Cómo abrirlo y ejecutarlo

1. Abrí Android Studio → **Open** → seleccioná esta carpeta.
2. Esperá el *Gradle Sync* (descarga dependencias y, si hace falta, el SDK 34).
3. Conectá un dispositivo/emulador y presioná **Run ▶**.

> Si preferís la línea de comandos:
> ```bash
> ./gradlew assembleDebug        # genera app/build/outputs/apk/debug/app-debug.apk
> ./gradlew installDebug         # instala en el dispositivo/emulador conectado
> ```

## Configurar la conexión (en la app)

1. Asegurate de que el celular esté en la **misma red Wi‑Fi** que la máquina con Docker (Mosquitto + Node-RED).
2. En la pantalla principal, ingresá la **IP local** de esa máquina (la misma de `MQTT_BROKER_HOST`).
3. Dejá el puerto en `1880` y el dispositivo en `corridor-01` (o el que corresponda).
4. Tocá el botón de **actualizar**. Si todo está bien, verás *"Servicio conectado"*.

## Notas técnicas

- Tráfico **HTTP en texto plano** habilitado (`network_security_config.xml`) porque Node-RED
  local no usa TLS.
- Arquitectura: **Jetpack Compose + Material 3**, `ViewModel` único compartido, **Retrofit/OkHttp/Gson**,
  **Navigation Compose**.
- **Tipografía:** sans-serif del sistema con un escalado cuidado. Para usar una fuente propia,
  ver las instrucciones en `ui/theme/Type.kt`.
- Paleta **clara y minimalista**: base crema + acentos salvia (stock) y terracota (seguridad),
  esquinas redondeadas y componentes estilizados.

## Estructura

```
app/src/main/java/com/soa/stocksecurity/
├── MainActivity.kt
├── data/            # Modelos, API Retrofit, repositorio y persistencia (SharedPreferences)
└── ui/
    ├── DeviceViewModel.kt   # Estado + lógica (GET/POST)
    ├── Format.kt            # Helpers de formato
    ├── components/          # Tarjetas, filas, chips, encabezados reutilizables
    ├── navigation/          # NavHost y rutas
    ├── screens/             # Principal, Stock, Seguridad
    └── theme/               # Color, tipografía y formas
```

## Prompt usado para generar la app

Esta app se construyó con *vibecoding*, usando **Claude Opus 4.8** en modo **Max thinking**. El prompt original fue:

```text
Objetivo: construir una app funcional de android que me permita establecer conexion con el ESP32, y que maneje los endpoints declarados en @.claude/context/infraestructura_nodeRed.md .

Contexto: @.claude/context/context_program.md

Que quiero que tenga esta app?

1. Una pantalla principal que:
a. me diga si el servicio esta conectado o no.
b. me permita actualizar manualmente
c. Burbuja que me permita activar el modo Stock (me lleve a otra pagina)
d. Burbuja que  me permita activar el modo seguridad (me lleve a otra pagina).


2. En la pagina de stock quiero poder ver los datos referentes a esta etapa y funcionalidad (usa los readme para entender que cosas poder mostrar)

3. En la pagina de seguridad poder ver los datos referentes a esta etapa y funcionalidad (usa los readme para entender que cosas poder mostrar).
a. Poder apagar o prender la alarma (buzer)


Layout:
El layout tiene que ser minimalista, clasico. ua componentes estilizados, marcos redondeados, Elegi un tipo de letra que quede bien con la gama de color clarito que estas usando.

Compatibilidad: el codigo debe de poderse usar en "andrioid estudio"
```
