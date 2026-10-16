# Prompt técnico — App de control de espejo inteligente (IoT)

## LENGUAJE DE PROGRAMACIÓN

- Java.
- Android Studio como entorno de desarrollo.
- UI con XML Views (Activities y Fragments).
- Código simple y modular: una clase por pantalla, sin mezclar lógica de conexión con lógica de UI.

## PLATAFORMA

- App nativa para Android, minSdk 26 o superior.
- Debe correr en emulador y en dispositivo físico.
- Se conecta a un espejo IoT a través de un broker **MQTT** (protocolo `tcp://`), cuya IP y puerto ingresa el usuario manualmente. No requiere backend propio: la comunicación es directa app ↔ broker MQTT ↔ hardware.
- Incluir un modo de datos simulados (activable por el usuario) que genere valores de telemetría ficticios, para poder probar toda la app sin tener el hardware ni un broker real disponibles.

## FUNCIONALIDAD A IMPLEMENTAR

Una app para controlar un espejo inteligente que:

1. Muestra en tiempo real la telemetría de dos sensores de distancia y un sensor de luz.
2. Permite un modo manual para regular la posición del espejo y la intensidad de una luz.
3. Permite encender/apagar la luz agitando el celular (detección de "shake" con el acelerómetro).
4. Muestra un historial gráfico de los valores de telemetría recibidos durante la sesión.
5. Alerta al usuario si permanece demasiado tiempo parado frente al espejo.

## REQUISITOS ESPECÍFICOS

### Comunicación MQTT

- Cliente MQTT: Eclipse Paho para Android (`org.eclipse.paho:org.eclipse.paho.client.mqttv3`).
- Tópicos de suscripción (telemetría entrante):
  - `espejo/sensor/distancia1`
  - `espejo/sensor/distancia2`
  - `espejo/sensor/luz`
- Tópicos de publicación (comandos salientes):
  - `espejo/control/modo` → `"manual"` / `"automatico"`
  - `espejo/control/servo` → entero 0–180
  - `espejo/control/luz` → entero 0–255
  - `espejo/control/led` → `"1"` / `"0"`
- Payloads en texto plano (no JSON).
- Los mensajes MQTT entrantes deben propagarse a las pantallas que los necesitan (por ejemplo con un broadcast local o un listener central), no leerse directamente desde cada pantalla.

### Pantalla 1 — Bienvenida

- Título de bienvenida, imagen ilustrativa, texto instructivo y botón "Comenzar".
- Al tocar "Comenzar": pedir el permiso de notificaciones (Android 13+) si no fue otorgado, y navegar a la pantalla de Conexión.

### Pantalla 2 — Conexión

- Campo de texto para IP, campo numérico para puerto, botón "Conectar", texto de estado, y un toggle "Usar datos simulados".
- Al presionar "Conectar" (en un hilo de background, sin bloquear la UI):
  - Si faltan IP o puerto (y no está activado el modo simulado): mostrar "Completá todos los campos".
  - Mientras conecta: mostrar "Conectando...".
  - Si conecta con éxito (o si el modo simulado está activo): navegar al Dashboard.
  - Si falla: mostrar "No se pudo conectar" y permanecer en la pantalla.

### Pantalla 3 — Dashboard

- Contenedor con una barra de navegación inferior de 3 secciones: **Sensores**, **Control**, **Historial**.
- Al entrar, suscribirse a los 3 tópicos de sensores y arrancar un servicio en segundo plano para la alerta de tiempo de uso (ver más abajo).
- Si se pierde la conexión MQTT, avisar al usuario (Toast o Snackbar).

### Sección Sensores

- 3 tarjetas: "Distancia 1", "Distancia 2" y "Luz", cada una con el valor recibido en tipografía grande.
- Antes de recibir el primer dato de cada sensor, mostrar un placeholder (por ejemplo "-- cm", "-- luz").
- Pantalla de solo lectura, sin acciones del usuario.

### Sección Control

- Switch "Manual": activa/desactiva el modo manual y publica el tópico `espejo/control/modo`.
- Slider de posición (0–180, valor inicial 90 = centro), con texto que indique si está a la izquierda, al centro o a la derecha del punto medio.
- Slider de intensidad de luz (0–255), con texto que muestre el valor numérico.
- Botón "Enviar", habilitado solo si el modo manual está activo: al presionarlo publica los valores actuales de ambos sliders (`espejo/control/servo` y `espejo/control/luz`). Los sliders no envían nada mientras se arrastran, solo al confirmar con el botón.
- Switch "Shake": solo puede activarse si el modo manual está activo (si no, revertirlo y avisar "Activar modo manual primero").
  - Al activarse, escuchar el acelerómetro (`TYPE_ACCELEROMETER`) y calcular la magnitud del vector de aceleración (`sqrt(x²+y²+z²)`).
  - Si supera el umbral **12**, y pasó más de **1000 ms** desde la última detección (para evitar múltiples disparos por una sola sacudida), alternar un estado de encendido/apagado y publicar `"1"`/`"0"` en `espejo/control/led`.
  - Dejar de escuchar el sensor al salir de la pantalla o al desactivar el switch.

### Sección Historial

- 3 gráficos de líneas (uno por sensor: distancia1, distancia2, luz), usando una librería de gráficos para Android (por ejemplo MPAndroidChart).
- Cada valor nuevo recibido por MQTT se agrega como un punto al gráfico correspondiente. Los datos se guardan solo en memoria durante la sesión, sin base de datos.
- Si todavía no llegó ningún dato de un sensor, el gráfico correspondiente queda vacío.

### Alerta de tiempo de uso

- Un servicio en primer plano (foreground service, con notificación persistente de baja prioridad) que:
  - Escucha los valores de los sensores de distancia.
  - Si la distancia es menor a un umbral (por ejemplo 390, en la misma unidad que reporta el sensor) de forma continua durante más de 5 segundos, dispara una notificación de alta prioridad avisando que se llevó mucho tiempo frente al espejo.
  - Resetea el conteo apenas la distancia vuelve a superar el umbral.
  - Se detiene al salir del Dashboard.

## RESTRICCIONES

- No usar datos reales sensibles (IPs o credenciales reales) como valores por defecto en el código.
- No agregar funcionalidades fuera de lo descripto acá (por ejemplo: sin login de usuario, sin backend en la nube, sin control de múltiples dispositivos), salvo el modo de datos simulados.
- No concentrar toda la lógica en una sola clase: separar claramente la comunicación MQTT, la lógica de cada pantalla y la UI.
- No dejar ninguna de las pantallas o secciones incompleta.
- No bloquear el hilo principal con operaciones de red; usar hilos de background o `AsyncTask`/`Thread` para conectar y publicar por MQTT.
- No agregar librerías o dependencias que no sean necesarias para cumplir los requisitos de este documento.
