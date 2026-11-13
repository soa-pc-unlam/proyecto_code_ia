# Prompt tecnico Android

Fuente del formato: `Clase-IA_1.pptx`, diapositivas 8 a 10.  
Estructura indicada por la catedra:

- LENGUAJE DE PROGRAMACION
- PLATAFORMA
- FUNCIONALIDADES A IMPLEMENTAR
- REQUISITOS ESPECIFICOS
- RESTRICCIONES

## Prompt utilizado

```text
LENGUAJE DE PROGRAMACION:
- Kotlin para Android.
- XML para las pantallas.
- Gradle Kotlin DSL para la configuracion del proyecto.

PLATAFORMA:
- Aplicacion Android nativa para smartphone.
- La app debe comunicarse con un ESP32 Auto Robot mediante MQTT.
- El sistema debe funcionar contra un broker MQTT configurable desde la app.

FUNCIONALIDADES A IMPLEMENTAR:
- Crear una pantalla principal de control manual del robot.
- Permitir enviar los comandos forward, backward, left, right y stop.
- Crear una pantalla de sensores para visualizar distancia izquierda,
  distancia derecha, nivel de luz y estado actual del robot.
- Permitir controlar el robot con el sensor de rotacion del smartphone.
- Permitir configurar host, puerto, usuario y password del broker MQTT.
- Suscribirse a los topics robot/sensors y robot/state.
- Publicar comandos en el topic robot/command.
- Mantener una version manual y una version con vibecoding para comparar
  metricas de CPU y memoria en Android.

REQUISITOS ESPECIFICOS:
- Usar SensorManager y SensorEventListener.
- Usar el sensor TYPE_ROTATION_VECTOR.
- Registrar el sensor en onResume y desregistrarlo en onPause.
- Enviar comandos MQTT solo cuando cambia la direccion detectada, para no
  saturar el broker.
- Interpretar robot/sensors con formato distanciaIzquierda;distanciaDerecha;luz.
- Interpretar robot/state para actualizar el estado visual del robot.
- Usar INTERNET permission en AndroidManifest.xml.
- La version manual debe usar el applicationId dev.mnovoa.SOA.
- La version con vibecoding debe usar el applicationId
  dev.mnovoa.SOA.vibecoding para poder instalar y medir ambas APK.
- Las metricas deben obtenerse con el script de la catedra
  get_cpu_mem_smarthpone.sh mediante ADB.

RESTRICCIONES:
- No bloquear el hilo principal de Android.
- No hardcodear credenciales finales del broker dentro del codigo fuente.
- No cambiar los topics MQTT definidos por el sistema embebido.
- No modificar manualmente los errores de la version vibecoding fuera del
  ciclo de refinamiento con IA.
- Medir 10 segundos sin interaccion y 10 segundos realizando la accion con el
  sensor Android, tanto en la version manual como en la version vibecoding.
```
