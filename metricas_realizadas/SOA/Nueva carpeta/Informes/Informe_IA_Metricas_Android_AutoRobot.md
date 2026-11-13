# Informe sobre IA y metricas Android - Auto Robot

Materia: Sistemas Operativos Avanzados  
Comision: Martes  
Grupo: M4  
Integrantes y DNI: COARITE COARITE, IVAN ENRIQUE (94447552); GALVEZ, EZEQUIEL MAXIMILIANO (37659307); GRAMAJO HECTOR MARCELO (34519918); NOVOA ROMERO, MARTÍN RICARDO (43087221); RIOS DI GAETA, BAUTISTA (46431397)  
Repositorio GitHub: https://github.com/UNLAM-SOA/2026-SOA-Q1-M4  
Fecha: 06/07/2026

## 1. Herramienta utilizada

Se utilizo OpenAI Codex como asistente de programacion para preparar, revisar y documentar la version Android con vibecoding del proyecto Auto Robot.

## 2. Modelo de IA utilizado

Modelo: OpenAI GPT-5 en entorno Codex.

## 3. Prompt tecnico utilizado

El prompt se estructuro con el formato tecnico indicado por la catedra y con el mismo criterio usado por otros grupos de la comision martes: contexto, stack tecnologico, funcionalidades, requisitos concretos, restricciones y medicion final.

El prompt completo queda documentado en `Informes/Prompt_Tecnico_Android.md`.

Resumen del prompt tecnico:

```text
LENGUAJE DE PROGRAMACION:
- Kotlin, XML y Gradle Kotlin DSL.

PLATAFORMA:
- Aplicacion Android nativa para smartphone conectada a un ESP32 Auto Robot por MQTT.

FUNCIONALIDADES A IMPLEMENTAR:
- Control manual del robot con comandos forward, backward, left, right y stop.
- Control por sensor de rotacion del smartphone.
- Pantalla de telemetria con distancia izquierda, distancia derecha, luz y estado.
- Configuracion de broker MQTT desde la app.
- Version manual y version con vibecoding para comparar metricas.

REQUISITOS ESPECIFICOS:
- Usar SensorManager, SensorEventListener y TYPE_ROTATION_VECTOR.
- Publicar comandos en robot/command.
- Suscribirse a robot/sensors y robot/state.
- Interpretar robot/sensors como distanciaIzquierda;distanciaDerecha;luz.
- Medir CPU y memoria Android con get_cpu_mem_smarthpone.sh.

RESTRICCIONES:
- No bloquear el hilo principal.
- No cambiar los topics MQTT.
- No hardcodear credenciales finales.
- Medir reposo y accion con sensor Android durante 10 segundos.
```

## 4. Refinamientos realizados

Durante la generacion y adaptacion se pidio que la version con vibecoding:

- Mantenga el mismo contrato MQTT que la version manual.
- Use comandos compatibles con el ESP32: `forward`, `backward`, `left`, `right`, `stop`.
- Conserve la pantalla de control manual y la pantalla de sensores.
- Use un `applicationId` diferente para poder instalar ambas APK en el mismo telefono.
- Permita ejecutar las metricas Android con el script oficial de la catedra.
- Mantenga el ciclo de vida correcto del sensor Android: registro en `onResume()` y liberacion en `onPause()`.
- No publique comandos repetidos si la direccion detectada no cambia.

## 5. Cantidad de tokens de IA utilizados

La herramienta utilizada fue Codex. Como no expone un contador total exportable para la sesion completa, se deja un valor estimado a partir del volumen de interacciones, analisis de codigo, generacion de documentacion, ajustes y validaciones realizadas.

Valor estimado registrado: 180.000 tokens.

## 6. Tiempo para obtener una version funcional

| Version Android | Tiempo de desarrollo | Observaciones |
|---|---:|---|
| Manual | Aprox. 10 h | Codigo base del repo en `Android/Proyecto_sin_Vibecoding`, desarrollado manualmente por el grupo. |
| Vibecoding total | Aprox. 4 h | Codigo preparado en `Android/Proyecto_con_Vibecoding` usando prompt tecnico, refinamientos, compilacion, documentacion y mediciones. |

## 7. Metricas CPU y memoria

Las metricas se realizan sobre Android, no sobre el ESP32. Se utiliza el script oficial enlazado por la catedra:

```text
Informes/Scripts/get_cpu_mem_smarthpone.sh
```

Link de origen:
https://gitlab.com/so-unlam/Material-SOA/-/tree/master/Ejemplos%20Android/UsoCpuMemAndroid

Paquetes a medir:

| Version | Paquete Android |
|---|---|
| Manual | `dev.mnovoa.SOA` |
| Vibecoding | `dev.mnovoa.SOA.vibecoding` |

Casos solicitados por la clase:

| Caso | Version | Situacion | Accion | Duracion |
|---|---|---|---|---:|
| 1 | Manual | Reposo | Abrir la app y no interactuar | 10 s |
| 2 | Manual | Accion con sensor | Abrir pantalla de sensores y mover/inclinar el telefono | 10 s |
| 3 | Vibecoding | Reposo | Abrir la app y no interactuar | 10 s |
| 4 | Vibecoding | Accion con sensor | Abrir pantalla de sensores y mover/inclinar el telefono | 10 s |

Estado actual de las mediciones:

Las mediciones reales fueron completadas el 07/07/2026 en un Samsung SM-A325M con Android 13, 8 nucleos y 3645.54 MB de RAM total. Ambas APK fueron instaladas por ADB en el mismo telefono. Se utilizo la columna 9 de CPU, validada previamente con `adb shell top`.

| Caso | PSS MB | RAM usada % | CPU promedio % | Observacion |
|---|---:|---:|---:|---|
| Manual reposo | 180.68 | 4.9563 | 241.00 | Sin interaccion. |
| Manual accion sensor | 190.00 | 5.2118 | 62.50 | Control por `TYPE_ROTATION_VECTOR`. |
| Vibecoding reposo | 204.00 | 5.5958 | 0.00 | Sin interaccion. |
| Vibecoding accion sensor | 197.39 | 5.4145 | 0.00 | Control por `TYPE_ROTATION_VECTOR`. |

En esta corrida, la version manual tuvo menor PSS en reposo, pero mayor consumo de CPU. La version con vibecoding uso algo mas de memoria, aunque se mantuvo estable en CPU durante las dos mediciones.

## 8. Procedimiento de medicion

1. Instalar la APK manual en el telefono.
2. Instalar la APK vibecoding en el telefono.
3. Conectar el telefono por USB con depuracion habilitada.
4. Copiar el script:

```bash
adb push Informes/Scripts/get_cpu_mem_smarthpone.sh /data/local/tmp/get_cpu_mem_smarthpone.sh
adb shell chmod 755 /data/local/tmp/get_cpu_mem_smarthpone.sh
```

5. Verificar la columna de CPU:

```bash
adb shell top
```

En la clase se usa como referencia la columna 9. En el telefono medido se confirmo la misma columna con `adb shell top`.

6. Ejecutar medicion manual:

```bash
adb shell sh /data/local/tmp/get_cpu_mem_smarthpone.sh dev.mnovoa.SOA 9
```

7. Ejecutar medicion vibecoding:

```bash
adb shell sh /data/local/tmp/get_cpu_mem_smarthpone.sh dev.mnovoa.SOA.vibecoding 9
```

8. Repetir cada comando en reposo y con accion de sensor. En la accion de sensor se debe abrir `SensorActivity` e inclinar el telefono durante la ventana de medicion.

## 9. Codigo en Git

Estructura solicitada:

```text
Android/
  Proyecto_sin_Vibecoding/
  Proyecto_con_Vibecoding/
Embebido/
  Proyecto_sin_Vibecoding/
  Proyecto_con_Vibecoding/
Informes/
```

Enlace al directorio Android manual:
https://github.com/UNLAM-SOA/2026-SOA-Q1-M4/tree/main/Android/Proyecto_sin_Vibecoding

Enlace al directorio Android vibecoding:
https://github.com/UNLAM-SOA/2026-SOA-Q1-M4/tree/main/Android/Proyecto_con_Vibecoding

## 10. Rubrica de funcionalidad Android

| Item | Manual | Vibecoding | Observacion |
|---|---|---|---|
| Compila | Si | Si | Verificado con `./gradlew.bat :app:assembleDebug`. |
| Ejecuta en telefono | Si | Si | Instalado y medido en Samsung SM-A325M por ADB. |
| Ejecucion en background | Parcial | Parcial | MQTT y Flow se procesan fuera del flujo directo de UI. |
| Sensores Android | Si | Si | Usa `TYPE_ROTATION_VECTOR`. |
| Permisos Android | Si | Si | Usa permiso `INTERNET`. |
| Comunicacion con embebido | Si | Si | Usa MQTT. |
| Cumplimiento funcional | Si | Si | Control manual, control por sensor y telemetria. |
| Diseno de app | Si | Si | Pantallas de control, sensores y configuracion. |

## 11. Conclusion

La actividad de IA queda orientada a Android. La comparacion se realizo entre la APK manual y la APK generada/adaptada con vibecoding, midiendo CPU y memoria con el script ADB de la catedra.

El enfoque con vibecoding acelera la obtencion de una version funcional porque permite generar estructura, pantallas, conexion MQTT y manejo de sensores a partir de un prompt tecnico. Sin embargo, requiere supervision humana para validar que los topics MQTT coincidan con el ESP32, que el sensor Android respete el ciclo de vida de la Activity y que el codigo no agregue complejidad innecesaria.

Las mediciones finales dependen del modelo del telefono, version de Android, RAM disponible, procesos activos y columna de CPU que devuelva `top`. Por eso se documento el dispositivo usado y se mantuvo el mismo entorno para las cuatro pruebas.
