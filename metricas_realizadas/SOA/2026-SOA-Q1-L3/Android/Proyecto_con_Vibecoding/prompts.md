# Especificación del Proyecto

## Lenguaje de Programación
- **Java**

## Plataforma
Desarrollar una app de Android con Internet de las cosas (IoT) que se conecte con MQTT a una serie de tópicos y envíe mensajes por Telegram.

---

# Funcionalidad a Implementar

El sistema actúa como un middleware o pasarela (Gateway) de asistencia para emergencias ("Nonófono"). Su función principal es recibir peticiones o alertas físicas provenientes de un microcontrolador (ESP32) vía MQTT, procesar dichos eventos de manera asíncrona, descargar audios vía HTTP cuando corresponda, y retransmitir las notificaciones y archivos multimedia a una lista de contactos familiares utilizando la API de un bot de Telegram.

A su vez, permite localizar el dispositivo físico enviando comandos desde el celular mediante la detección de movimientos bruscos.

---

# Requisitos Específicos

## Arquitectura de Negocio

Toda la lógica de negocio, persistencia de datos (SharedPreferences) y manipulación de la lista de contactos debe estar rigurosamente encapsulada en una clase Gestor (por ejemplo, `ContactosManager`), dejando a los controladores (Activities) con la única responsabilidad de manejar la vista.

---

## Actividades y XML

### MainActivity (`activity_main.xml`)

Panel de control central.

Debe contener:

- Botones de navegación hacia las pantallas de agregar contacto y localizar dispositivo.
- Etiquetas de texto (`TextView`) para monitorear visualmente el estado de conexión de Telegram y MQTT en tiempo real.
- Se encarga de lanzar el servicio de red.

### Agregar contactos

Módulo para gestionar el directorio.

Debe incluir:

- Un formulario con validación por expresiones regulares (entre 8 y 15 caracteres).
- Un `RecyclerView` (con su respectivo `Adapter`) para:
  - Listar contactos.
  - Añadir contactos (hasta un máximo de 10).
  - Eliminar contactos.

### Localizar dispositivo

Pantalla de rastreo.

Debe implementar obligatoriamente el sensor del acelerómetro.

Si el usuario agita el celular (*shake*) y la magnitud supera un umbral de **15.0f**, se debe disparar un evento (con un *cooldown* de **6 segundos**) que publique un mensaje MQTT para hacer sonar el hardware remoto.

---

## Servicio en Segundo Plano

Implementar un **Foreground Service** con la bandera `START_STICKY` para asegurar que el cliente MQTT (operando como Singleton) se mantenga vivo y suscrito a los eventos aunque la app esté minimizada.

---

## Notificación Persistente

El servicio debe anclar una notificación en el sistema (`NotificationChannel`) que informe que el "Nonófono" está activo.

Debe incluir un `PendingIntent` con un botón de acción explícito para detener el servicio y destruir la conexión de red de manera segura.

---

## Descarga y Envío de Audio

Crear una clase encargada de descargar audio que ejecute peticiones HTTP GET (puerto 80) hacia el ESP32 para descargar archivos `.wav`.

Una vez guardados en el directorio local, estos deben enviarse a los chats correspondientes consumiendo el endpoint `/sendAudio` de Telegram utilizando el formato `multipart/form-data`.

---

## Tópicos MQTT

### Suscripciones (Subscribe)

- `nonofono/emergencias`
- `nonofono/mensajes/audio`
- `nonofono/mensajes/predeterminado`
- `nonofono/live`

### Publicaciones (Publish)

- `nonofono/encontrar` (al agitar el celular)
- `nonofono/mensajes/audio/ack`
- `nonofono/mensajes/predeterminado/ack` (para confirmación de envíos)
- `nonofono/config/contactos` (para inyectar la agenda al hardware)

---

# Restricciones

- Debe funcionar como mínimo en Android 10 (API 29), contemplando permisos modernos como `POST_NOTIFICATIONS` para Android 13+.
- Debe ser una implementación segura, robusta y con abundantes mensajes de debug (ej. `Log.i`, `Log.e`, `Log.w`).
- La sincronización entre los hilos de red y la interfaz gráfica debe manejarse de forma no bloqueante utilizando `BroadcastReceivers` y `runOnUiThread`.
- Debe conectarse a Telegram mediante el token dado del bot:
  ```
  8322654023:AAHhtylDzV06H71m-Xko3mhbg2TQ3J5brdA
  ```
- Debe conectarse a MQTT de forma segura con SSL (`SSLSocketFactory.getDefault()`) con los siguientes datos:

  **Cluster**
  ```
  ssl://e32525b034cd4ef5a01f6e9715295f64.s1.eu.hivemq.cloud
  ```

  **Username**
  ```
  admin_prueba
  ```

  **Password**
  ```
  Nonofono8
  ```

- Tolerancia a fallos de red:
  - Todas las conexiones (HTTP para descargas y API REST de Telegram) deben implementar **Timeouts estrictos**.
  - Las desconexiones MQTT deben aniquilar el objeto cliente y limpiar los descriptores de red (`client.close()`) para evitar conexiones fantasma (*Half-Open connections*).

- El proyecto ya estará creado con valores iniciales, por lo que **no debes inicializar el proyecto de cero**.

---

# Tipos de Datos

El código del microcontrolador ESP32 es inmutable.

Por lo tanto, la aplicación Android debe parsear y serializar los datos respetando estrictamente los siguientes formatos JSON asociados a cada tópico MQTT.

---

## Entrada (Datos recibidos desde el ESP32)

Todos los mensajes entrantes comparten una estructura raíz compuesta por las claves `"evento"` y `"contenido"`.

### Tópico `nonofono/emergencias`

```json
{
  "evento": "emergencia",
  "contenido": {
    "mensaje": "Texto a enviar por Telegram"
  }
}
```

### Tópico `nonofono/mensajes/audio`

```json
{
  "evento": "audio",
  "contenido": {
    "ip": "192.168.0.X",
    "endpoint": "/descargar_audio",
    "id_mensaje": 123,
    "destinatario": "1122334455"
  }
}
```

### Tópico `nonofono/mensajes/predeterminado`

```json
{
  "evento": "mensaje-predefinido",
  "contenido": {
    "telefono": "1122334455",
    "contenido": "Mensaje de texto rápido"
  }
}
```

### Tópico `nonofono/live`

```json
{
  "evento": "live",
  "contenido": {}
}
```

---

## Salida (Datos enviados hacia el ESP32)

### Tópico `nonofono/config/contactos`

Debe publicarse **500 ms después** de recibir el evento `live`.

```json
[
  {
    "name": "Juan",
    "phone": "1122334455"
  },
  {
    "name": "Maria",
    "phone": "1199887766"
  }
]
```

### Tópico `nonofono/encontrar`

```json
{
  "action": "localizar"
}
```

### Tópico `nonofono/mensajes/audio/ack`

```json
{
  "exito": "Ok"
}
```

### Tópico `nonofono/mensajes/predeterminado/ack`

```json
{
  "exito": "Ok"
}
```

# RESULTADOS DE LA SESION

## Android manifest

foregroundServiceType:connectedDevice requires permission:[android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE]

## AAgregarContactosActivity

Unused import statement

## AudioDownloader

Unused import statement 14
Unused import statement 17
Class 'AudioDownloader' is never used
Method 'downloadAudio(android.content.Context, java.lang.String)' is never used
Method 'sendAudioToTelegram(java.lang.String, java.lang.String, java.io.File)' is never used
Value of parameter 'name' is always '"chat_id"'
Value of parameter 'fieldName' is always '"audio"'
Value 'fileInput' is always 'null'

## Contactos Adapter
Class 'ContactoViewHolder' is exposed outside its defined visibility scope 30
Class 'ContactoViewHolder' is exposed outside its defined visibility scope 36
It will always be more efficient to use more specific change events if you can. Rely on `notifyDataSetChanged` as a last resort.

## ContactosManager

Unused import statement 9
Calls to boolean method 'validarContacto()' are always inverted 70

## LocalizarActivity
sin errores

## MainActivity
**Presenta errores**
`statusReceiver` is missing `RECEIVER_EXPORTED` or `RECEIVER_NOT_EXPORTED` flag for unprotected broadcasts registered for com.example.vibenofono.ACTION_MQTT_STATUS, com.example.vibenofono.ACTION_TELEGRAM_STATUS
Unnecessary; `SDK_INT` is always >= 29

## MqttClientSingleton
**Presenta errores**

Cannot resolve symbol 'eclipse'
Cannot resolve symbol 'eclipse'
Cannot resolve symbol 'eclipse'
Cannot resolve symbol 'eclipse'
Cannot resolve symbol 'eclipse'
Cannot resolve symbol 'eclipse'
Cannot resolve symbol 'eclipse'
Cannot resolve symbol 'eclipse'
Cannot resolve symbol 'eclipse'
Cannot resolve symbol 'MqttClient'
Cannot resolve symbol 'MqttCallback'
Cannot resolve symbol 'MqttCallback'
Cannot resolve method 'isConnected()'
Cannot resolve symbol 'MqttClient'
Cannot resolve symbol 'MemoryPersistence'
Cannot resolve symbol 'MqttConnectOptions'
Cannot resolve symbol 'MqttConnectOptions'
Cannot resolve method 'setAutomaticReconnect(boolean)'
Cannot resolve method 'setCleanSession(boolean)'
Cannot resolve method 'setConnectionTimeout(int)'
Cannot resolve method 'setKeepAliveInterval(int)'
Cannot resolve method 'setCallback(MqttCallback)'
Cannot resolve method 'connect(MqttConnectOptions)'
Cannot resolve symbol 'MqttException'
'e(java.lang.String, java.lang.String, java.lang.Throwable)' in 'android.util.Log' cannot be applied to '(java.lang.String, java.lang.String, MqttException)'
Cannot resolve method 'isConnected()'
Cannot resolve method 'subscribe(String, int)'
Cannot resolve method 'subscribe(String, int)'
Cannot resolve method 'subscribe(String, int)'
Cannot resolve method 'subscribe(String, int)'
Cannot resolve symbol 'MqttException'
'e(java.lang.String, java.lang.String, java.lang.Throwable)' in 'android.util.Log' cannot be applied to '(java.lang.String, java.lang.String, MqttException)'
Cannot resolve method 'isConnected()'
Cannot resolve symbol 'MqttMessage'
Cannot resolve symbol 'MqttMessage'
Cannot resolve method 'setQos(int)'
Cannot resolve method 'publish(String, MqttMessage)'
Cannot resolve symbol 'MqttException'
'e(java.lang.String, java.lang.String, java.lang.Throwable)' in 'android.util.Log' cannot be applied to '(java.lang.String, java.lang.String, MqttException)'
Cannot resolve method 'disconnect()'
Cannot resolve method 'close()'
Cannot resolve symbol 'MqttException'
'e(java.lang.String, java.lang.String, java.lang.Throwable)' in 'android.util.Log' cannot be applied to '(java.lang.String, java.lang.String, MqttException)'
Unused import statement
Unused import statement
Unused import statement
Field can be converted to a local variable
Private field 'context' is assigned but never accessed
Field can be converted to a local variable
Private field 'callback' is assigned but never accessed

## NonofonoService
**Presenta errores**

Cannot resolve symbol 'eclipse'
Cannot resolve symbol 'eclipse'
Cannot resolve symbol 'MqttCallback'
Cannot resolve symbol 'MqttMessage'
Cannot resolve method 'getPayload()'
Cannot resolve symbol 'eclipse'
Private field 'mqttConnected' is assigned but never accessed
Method 'connectionLost(java.lang.Throwable)' is never used
Method 'messageArrived(java.lang.String, MqttMessage)' is never used
Method 'deliveryComplete(org.eclipse.paho.client.mqttv3.IMqttDeliveryToken)' is never used
Parameter 'token' is never used
'if' statement can be replaced with 'switch' statement
Unnecessary; `SDK_INT` is always >= 29
Unnecessary; `SDK_INT` is always >= 29
Parameter 'context' is never used

## NonofonoServiceReceiver

Unused import statement
Field 'INSTANCE' is never used
'return' is unnecessary as the last statement in a 'void' method

# Prompt respuesta 1
Actualicé el archivo de prompts con todos los errores y warnings que encontré en el programa. Por favor, soluciónalos y completa los pendientes que dejaste.

# Resultado
Tuvimos que hacer un sync now manualmente. El agente se encargó de buildear el proyecto y revisar y corregir sus errores. Tenemos la primera versión funcional (que buildea) del proyecto. Sin embargo, apenas se abre la app esta se crashea. La app no presenta errores ni warnings relevantes.

## Logs de logcat
2026-07-05 20:29:24.586 26888-26888 nativeloader            com.example.vibenofono               D  Configuring clns-7 for other apk /data/app/~~I_O3pQ-WlUEahn8PMlaLDg==/com.example.vibenofono-0lGa9MyZul6IGKqkSmvgoA==/base.apk. target_sdk_version=36, uses_libraries=, library_path=/data/app/~~I_O3pQ-WlUEahn8PMlaLDg==/com.example.vibenofono-0lGa9MyZul6IGKqkSmvgoA==/lib/arm64, permitted_path=/data:/mnt/expand:/data/user/0/com.example.vibenofono
2026-07-05 20:29:24.596 26888-26888 GraphicsEnvironment     com.example.vibenofono               V  Currently set values for:
2026-07-05 20:29:24.596 26888-26888 GraphicsEnvironment     com.example.vibenofono               V    angle_gl_driver_selection_pkgs=[]
2026-07-05 20:29:24.596 26888-26888 GraphicsEnvironment     com.example.vibenofono               V    angle_gl_driver_selection_values=[]
2026-07-05 20:29:24.596 26888-26888 GraphicsEnvironment     com.example.vibenofono               V  Global.Settings values are invalid: number of packages: 0, number of values: 0
2026-07-05 20:29:24.596 26888-26888 GraphicsEnvironment     com.example.vibenofono               V  App is not on the allowlist for updatable production driver.
2026-07-05 20:29:24.614 26888-26888 Typeface                com.example.vibenofono               I  updateThemeFont new=Rookery-Regular;old=sans-serif;new=Rookery-Stacked;old=
2026-07-05 20:29:24.619 26888-26910 libMEOW                 com.example.vibenofono               D  meow new tls: 0xb4000071ccc0f090
2026-07-05 20:29:24.619 26888-26910 libMEOW                 com.example.vibenofono               D  meow reload base cfg path: na
2026-07-05 20:29:24.619 26888-26910 libMEOW                 com.example.vibenofono               D  meow reload overlay cfg path: na
2026-07-05 20:29:24.619 26888-26910 QT                      com.example.vibenofono               W  qt_process_init() called
2026-07-05 20:29:24.619 26888-26910 QT                      com.example.vibenofono               E  [QT]file does not exist
2026-07-05 20:29:24.619 26888-26910 QT                      com.example.vibenofono               W  Support!!
2026-07-05 20:29:24.620 26888-26910 QT                      com.example.vibenofono               E  [QT]file does not exist
2026-07-05 20:29:24.620 26888-26910 libMEOW                 com.example.vibenofono               D  applied 1 plugins for [com.example.vibenofono]:
2026-07-05 20:29:24.620 26888-26910 libMEOW                 com.example.vibenofono               D    plugin 1: [libMEOW_gift.so]:
2026-07-05 20:29:24.621 26888-26910 libMEOW                 com.example.vibenofono               D  meow delete tls: 0xb4000071ccc0f090
2026-07-05 20:29:24.641 26888-26888 AppCompatDelegate       com.example.vibenofono               D  Checking for metadata for AppLocalesMetadataHolderService : Service not found
2026-07-05 20:29:24.696 26888-26888 mple.vibenofono         com.example.vibenofono               I  hiddenapi: Accessing hidden method Landroid/view/ViewGroup;->makeOptionalFitsSystemWindows()V (runtime_flags=0, domain=platform, api=unsupported) from Landroidx/appcompat/widget/ViewUtils; (domain=app, TargetSdkVersion=36) using reflection: allowed
2026-07-05 20:29:24.778 26888-26888 AndroidRuntime          com.example.vibenofono               D  Shutting down VM
2026-07-05 20:29:24.781 26888-26888 AndroidRuntime          com.example.vibenofono               E  FATAL EXCEPTION: main
                                                                                                    Process: com.example.vibenofono, PID: 26888
                                                                                                    java.lang.RuntimeException: Unable to create service com.example.vibenofono.NonofonoService: java.lang.SecurityException: Starting FGS with type connectedDevice callerApp=ProcessRecord{1ac60d5 26888:com.example.vibenofono/u0a428} targetSDK=36 requires permissions: all of the permissions allOf=true [android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE] any of the permissions allOf=false [android.permission.BLUETOOTH_ADVERTISE, android.permission.BLUETOOTH_CONNECT, android.permission.BLUETOOTH_SCAN, android.permission.CHANGE_NETWORK_STATE, android.permission.CHANGE_WIFI_STATE, android.permission.CHANGE_WIFI_MULTICAST_STATE, android.permission.NFC, android.permission.TRANSMIT_IR, android.permission.UWB_RANGING, USB Device, USB Accessory] 
                                                                                                    	at android.app.ActivityThread.handleCreateService(ActivityThread.java:5124)
                                                                                                    	at android.app.ActivityThread.-$$Nest$mhandleCreateService(ActivityThread.java:0)
                                                                                                    	at android.app.ActivityThread$H.handleMessage(ActivityThread.java:2559)
                                                                                                    	at android.os.Handler.dispatchMessage(Handler.java:112)
                                                                                                    	at android.os.Looper.loopOnce(Looper.java:268)
                                                                                                    	at android.os.Looper.loop(Looper.java:384)
                                                                                                    	at android.app.ActivityThread.main(ActivityThread.java:8936)
                                                                                                    	at java.lang.reflect.Method.invoke(Native Method)
                                                                                                    	at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:580)
                                                                                                    	at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:907)
                                                                                                    Caused by: java.lang.SecurityException: Starting FGS with type connectedDevice callerApp=ProcessRecord{1ac60d5 26888:com.example.vibenofono/u0a428} targetSDK=36 requires permissions: all of the permissions allOf=true [android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE] any of the permissions allOf=false [android.permission.BLUETOOTH_ADVERTISE, android.permission.BLUETOOTH_CONNECT, android.permission.BLUETOOTH_SCAN, android.permission.CHANGE_NETWORK_STATE, android.permission.CHANGE_WIFI_STATE, android.permission.CHANGE_WIFI_MULTICAST_STATE, android.permission.NFC, android.permission.TRANSMIT_IR, android.permission.UWB_RANGING, USB Device, USB Accessory] 
                                                                                                    	at android.os.Parcel.createExceptionOrNull(Parcel.java:3241)
                                                                                                    	at android.os.Parcel.createException(Parcel.java:3225)
                                                                                                    	at android.os.Parcel.readException(Parcel.java:3208)
                                                                                                    	at android.os.Parcel.readException(Parcel.java:3150)
                                                                                                    	at android.app.IActivityManager$Stub$Proxy.setServiceForeground(IActivityManager.java:7337)
                                                                                                    	at android.app.Service.startForeground(Service.java:776)
                                                                                                    	at com.example.vibenofono.NonofonoService.onCreate(NonofonoService.java:42)
                                                                                                    	at android.app.ActivityThread.handleCreateService(ActivityThread.java:5111)
                                                                                                    	at android.app.ActivityThread.-$$Nest$mhandleCreateService(ActivityThread.java:0) 
                                                                                                    	at android.app.ActivityThread$H.handleMessage(ActivityThread.java:2559) 
                                                                                                    	at android.os.Handler.dispatchMessage(Handler.java:112) 
                                                                                                    	at android.os.Looper.loopOnce(Looper.java:268) 
                                                                                                    	at android.os.Looper.loop(Looper.java:384) 
                                                                                                    	at android.app.ActivityThread.main(ActivityThread.java:8936) 
                                                                                                    	at java.lang.reflect.Method.invoke(Native Method) 
                                                                                                    	at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:580) 
                                                                                                    	at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:907) 
                                                                                                    Caused by: android.os.RemoteException: Remote stack trace:
                                                                                                    	at com.android.server.am.ActiveServices.validateForegroundServiceType(ActiveServices.java:2921)
                                                                                                    	at com.android.server.am.ActiveServices.setServiceForegroundInnerLocked(ActiveServices.java:2605)
                                                                                                    	at com.android.server.am.ActiveServices.setServiceForegroundLocked(ActiveServices.java:1859)
                                                                                                    	at com.android.server.am.ActivityManagerService.setServiceForeground(ActivityManagerService.java:14555)
                                                                                                    	at android.app.IActivityManager$Stub.onTransact$setServiceForeground$(IActivityManager.java:12232)
2026-07-05 20:29:24.790 26888-26888 Process                 com.example.vibenofono               I  Sending signal. PID: 26888 SIG: 9
---------------------------- PROCESS ENDED (26888) for package com.example.vibenofono ----------------------------

## Prompt Respuesta 2
La app efectivamente buildea y la pude pasar a mi smartphone. Cuando intenté usarla, apenas abrirla me tiró esto (adjunto de logs de logcat). Por favor encuentra el error y solucionalo

## Resultado

El mismo resultado anterior.

## Prompt 3
(log de logcat)
Me tiró eso. Resolvelo por favor. Revisa bien el tema de los foreground services y servicios en general para que no vuelva a pasar


## Resultado

El mismo resultado anterior.

## Prompts 4 a 6
Se enfocaron en solucionar problemas de permisos en el androidManifest. La app finalmente conecta y envia el mensaje de localizar
dispositivo, pero falla en los contactos (solo pide el numero y no lo pide en teclado numerico)

## Prompt 7
Me saltó esto al intentar conectar al MQTT desde logcat. Por favor revisa el código y solucionalo
MQTT connection lost
                                                                                                    Se ha perdido la conexión (32109) - java.io.EOFException
                                                                                                    	at org.eclipse.paho.client.mqttv3.internal.CommsReceiver.run(CommsReceiver.java:197)
                                                                                                    	at java.lang.Thread.run(Thread.java:1572)
                                                                                                    Caused by: java.io.EOFException
                                                                                                    	at java.io.DataInputStream.readByte(DataInputStream.java:275)
                                                                                                    	at org.eclipse.paho.client.mqttv3.internal.wire.MqttInputStream.readMqttWireMessage(MqttInputStream.java:92)
                                                                                                    	at org.eclipse.paho.client.mqttv3.internal.CommsReceiver.run(CommsReceiver.java:137)
                                                                                                    	at java.lang.Thread.run(Thread.java:1572)
+120 lineas, 3.6 creditos
## Errores

## Android manifest
Class referenced in the manifest, `org.eclipse.paho.android.service.MqttService`, was not found in the project or the libraries
Unresolved package 'android'
Unresolved package 'service'
Unresolved class 'MqttService'

## MqttClientSingleton
Cannot resolve symbol 'android'
Cannot resolve symbol 'MqttAndroidClient'
Cannot resolve method 'isConnected()'
Cannot resolve symbol 'MqttAndroidClient'
Cannot resolve method 'setCallback(MqttCallback)'
Cannot resolve method 'connect(MqttConnectOptions, null, IMqttActionListener)'
Cannot resolve method 'isConnected()'
Cannot resolve method 'subscribe(String, int)'
Cannot resolve method 'subscribe(String, int)'
Cannot resolve method 'subscribe(String, int)'
Cannot resolve method 'subscribe(String, int)'
Cannot resolve method 'isConnected()'
Cannot resolve method 'publish(String, MqttMessage)'
Cannot resolve method 'disconnect()'
Cannot resolve method 'close()'

## Prompt 8

La app no buildea y me tira todos estos errores

## Android manifest
Class referenced in the manifest, `org.eclipse.paho.android.service.MqttService`, was not found in the project or the libraries
Unresolved package 'android'
Unresolved package 'service'
Unresolved class 'MqttService'

## MqttClientSingleton
Cannot resolve symbol 'android'
Cannot resolve symbol 'MqttAndroidClient'
Cannot resolve method 'isConnected()'
Cannot resolve symbol 'MqttAndroidClient'
Cannot resolve method 'setCallback(MqttCallback)'
Cannot resolve method 'connect(MqttConnectOptions, null, IMqttActionListener)'
Cannot resolve method 'isConnected()'
Cannot resolve method 'subscribe(String, int)'
Cannot resolve method 'subscribe(String, int)'
Cannot resolve method 'subscribe(String, int)'
Cannot resolve method 'subscribe(String, int)'
Cannot resolve method 'isConnected()'
Cannot resolve method 'publish(String, MqttMessage)'
Cannot resolve method 'disconnect()'
Cannot resolve method 'close()'

Creo que intentaste crear un service cuando no es necesario y a raiz de eso arrastraste el error, o lo creaste mal y no reconoce los comandos que antes sí reconocía.

## Prompt 9
El servicio si o si necesita el permiso de dataSync/mediaPlayback que borraste antes para funcionar por cuestiones de seguridad. Agregalo. 
**Tuvimos que indicarle manualmente qué cambiar debido a que insistía en eliminar los permisos/no colocarlos.**