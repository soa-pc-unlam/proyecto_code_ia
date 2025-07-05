# App Cursor MQTT

## Descripción
Aplicación Android desarrollada en Java que implementa funcionalidades MQTT y detección de movimiento del dispositivo.

## Funcionalidades

### Primera Actividad (MainActivity)
- **Botón 1**: Publica mensajes en el tópico `/casa/luz` del broker MQTT `test.mosquitto.org`
- **Botón 2**: Abre la segunda actividad (ShakeActivity)
- **TextBox**: Muestra los mensajes recibidos del tópico `/casa/cmd`

### Segunda Actividad (ShakeActivity)
- Detecta el movimiento de agitación (shake) del dispositivo
- Cambia el color de fondo entre: Rojo, Negro, Azul, Verde y Amarillo
- Botón para volver a la actividad principal

## Requisitos Técnicos

### Dependencias
- Android SDK 24+
- Eclipse Paho MQTT Client
- Sensores de acelerómetro del dispositivo

### Permisos
- `INTERNET`: Para conexión MQTT
- `ACCESS_NETWORK_STATE`: Para verificar conectividad
- `WAKE_LOCK`: Para mantener la conexión MQTT activa

## Configuración MQTT

### Broker
- **URL**: `test.mosquitto.org`
- **Puerto**: `1883`
- **Protocolo**: TCP

### Tópicos
- **Publicación**: `/casa/luz`
- **Suscripción**: `/casa/cmd`

## Instalación

1. Clonar el repositorio
2. Abrir el proyecto en Android Studio
3. Sincronizar las dependencias de Gradle
4. Compilar y ejecutar en un dispositivo Android

## Uso

1. **Conexión MQTT**: La aplicación se conecta automáticamente al broker al iniciar
2. **Publicar mensajes**: Presionar el botón "Publicar Mensaje" para enviar un mensaje al tópico `/casa/luz`
3. **Recibir mensajes**: Los mensajes del tópico `/casa/cmd` aparecen automáticamente en el textbox
4. **Actividad Shake**: Presionar "Abrir Actividad Shake" y agitar el dispositivo para cambiar colores

## Estructura del Proyecto

```
app/
├── src/main/
│   ├── java/com/example/appcursormqtt/
│   │   ├── MainActivity.java          # Actividad principal con MQTT
│   │   └── ShakeActivity.java         # Actividad de detección de shake
│   ├── res/layout/
│   │   ├── activity_main.xml          # Layout de la actividad principal
│   │   └── activity_shake.xml         # Layout de la actividad shake
│   └── AndroidManifest.xml            # Configuración de la app
└── build.gradle.kts                   # Dependencias del proyecto
```

## Características Técnicas

### Detección de Shake
- Utiliza el sensor de acelerómetro
- Umbral de detección configurable
- Filtrado temporal para evitar detecciones múltiples

### Gestión MQTT
- Conexión automática al iniciar
- Reconexión automática en caso de pérdida
- Callbacks para manejo de mensajes
- Limpieza de recursos al cerrar

## Notas de Desarrollo

- Desarrollado en Java para Android
- Utiliza ConstraintLayout para interfaces responsivas
- Implementa patrones de callback para MQTT
- Manejo adecuado del ciclo de vida de las actividades 