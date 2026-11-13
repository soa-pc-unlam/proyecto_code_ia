# metricasCPU - Android

Materia: Sistemas Operativos Avanzados  
Comision: Martes  
Grupo: M4  
Integrantes: COARITE COARITE, IVAN ENRIQUE (94447552); GALVEZ, EZEQUIEL MAXIMILIANO (37659307); GRAMAJO HECTOR MARCELO (34519918); NOVOA ROMERO, MARTÍN RICARDO (43087221); RIOS DI GAETA, BAUTISTA (46431397)  
Proyecto: Auto Robot Android

## Estado actual

Mediciones reales completadas el 07/07/2026 con un telefono Samsung SM-A325M conectado por ADB. En esta maquina quedo instalado un SDK Android local en `D:\Facultad\FALTANTES\SOA\PARCIAL\.android-sdk`, con `adb`, Android SDK Platform 36 y Build-Tools 36.1.0. Se instalaron ambas APK y se midieron los cuatro casos pedidos por la catedra.

Columna de CPU utilizada: 9, validada con `adb shell top` en el dispositivo de prueba.

## Objetivo

Medir y comparar el uso promedio de CPU y memoria de la aplicacion Android en dos versiones del proyecto:

- Version manual: `Android/Proyecto_sin_Vibecoding`.
- Version con vibecoding: `Android/Proyecto_con_Vibecoding`.

Aunque el archivo se mantiene como `metricasCPU` por compatibilidad con la entrega, las mediciones corresponden a Android y se hacen con ADB usando el script oficial de la catedra.

## Script utilizado

Archivo local:

```text
Informes/Scripts/get_cpu_mem_smarthpone.sh
```

Script auxiliar para Windows:

```text
Informes/Scripts/run_metricas_android.ps1
```

Origen:

```text
https://gitlab.com/so-unlam/Material-SOA/-/tree/master/Ejemplos%20Android/UsoCpuMemAndroid
```

El script informa:

- Modelo del dispositivo.
- Version de Android.
- Cantidad de nucleos.
- RAM total.
- Paquete medido.
- PSS de la aplicacion.
- Porcentaje de RAM utilizado.
- CPU promedio durante 10 muestras.

## Paquetes Android

| Version | Paquete |
|---|---|
| Manual | `dev.mnovoa.SOA` |
| Vibecoding | `dev.mnovoa.SOA.vibecoding` |

## Preparacion

1. Instalar la APK manual y la APK con vibecoding en el telefono.
2. Activar opciones de desarrollador y depuracion USB.
3. Conectar el telefono por USB.
4. Verificar conexion:

```bash
adb devices
```

5. Copiar el script:

```bash
adb push Informes/Scripts/get_cpu_mem_smarthpone.sh /data/local/tmp/get_cpu_mem_smarthpone.sh
adb shell chmod 755 /data/local/tmp/get_cpu_mem_smarthpone.sh
```

6. Verificar la columna de CPU:

```bash
adb shell top
```

En las diapositivas de la catedra se usa la columna 9 como referencia. Si en el telefono la columna `%CPU` aparece en otra posicion, usar ese numero.

## APKs generadas localmente

Las dos APK compilaron correctamente con:

```powershell
.\gradlew.bat :app:assembleDebug
```

Rutas:

```text
Android/Proyecto_sin_Vibecoding/app/build/outputs/apk/debug/app-debug.apk
Android/Proyecto_con_Vibecoding/app/build/outputs/apk/debug/app-debug.apk
```

## Ejecucion guiada en Windows

Con el telefono conectado y autorizado en ADB:

```powershell
cd D:\Facultad\FALTANTES\SOA\PARCIAL\.codex_repo_m4\Informes\Scripts
.\run_metricas_android.ps1
```

Si la columna de CPU no es 9:

```powershell
.\run_metricas_android.ps1 -CpuColumn 10
```

## Casos de prueba

| Caso | Version | Situacion | Accion dentro de la ventana | Duracion |
|---|---|---|---|---:|
| 1 | Manual | Reposo | Abrir la app y no interactuar | 10 s |
| 2 | Manual | Accion con sensor | Abrir `SensorActivity` e inclinar/mover el telefono | 10 s |
| 3 | Vibecoding | Reposo | Abrir la app y no interactuar | 10 s |
| 4 | Vibecoding | Accion con sensor | Abrir `SensorActivity` e inclinar/mover el telefono | 10 s |

## Comandos

Manual en reposo o accion:

```bash
adb shell sh /data/local/tmp/get_cpu_mem_smarthpone.sh dev.mnovoa.SOA 9
```

Vibecoding en reposo o accion:

```bash
adb shell sh /data/local/tmp/get_cpu_mem_smarthpone.sh dev.mnovoa.SOA.vibecoding 9
```

## Resultado resumido

| Caso | Modelo | Android | Nucleos | RAM total | PSS MB | RAM usada % | CPU promedio % |
|---|---|---|---:|---:|---:|---:|---:|
| Manual reposo | SM-A325M | 13 | 8 | 3645.54 MB | 180.68 | 4.9563 | 241.00 |
| Manual accion sensor | SM-A325M | 13 | 8 | 3645.54 MB | 190.00 | 5.2118 | 62.50 |
| Vibecoding reposo | SM-A325M | 13 | 8 | 3645.54 MB | 204.00 | 5.5958 | 0.00 |
| Vibecoding accion sensor | SM-A325M | 13 | 8 | 3645.54 MB | 197.39 | 5.4145 | 0.00 |

## Salidas del script

### 1. Manual en reposo

```text
========================================
Informacion del dispositivo
========================================
Modelo: SM-A325M
Android: 13
Cant. Nucleos: 8
RAM Total: 3645.54 MB

========================================
Informacion de la aplicacion
========================================
Paquete: dev.mnovoa.SOA
PSS: 180.68 MB
Porcentaje RAM utilizada: 4.9563 %
CPU promedio: 241.00 %

========================================
```

### 2. Manual con accion de sensor

```text
========================================
Informacion del dispositivo
========================================
Modelo: SM-A325M
Android: 13
Cant. Nucleos: 8
RAM Total: 3645.54 MB

========================================
Informacion de la aplicacion
========================================
Paquete: dev.mnovoa.SOA
PSS: 190.00 MB
Porcentaje RAM utilizada: 5.2118 %
CPU promedio: 62.50 %

========================================
```

### 3. Vibecoding en reposo

```text
========================================
Informacion del dispositivo
========================================
Modelo: SM-A325M
Android: 13
Cant. Nucleos: 8
RAM Total: 3645.54 MB

========================================
Informacion de la aplicacion
========================================
Paquete: dev.mnovoa.SOA.vibecoding
PSS: 204.00 MB
Porcentaje RAM utilizada: 5.5958 %
CPU promedio: 0.00 %

========================================
```

### 4. Vibecoding con accion de sensor

```text
========================================
Informacion del dispositivo
========================================
Modelo: SM-A325M
Android: 13
Cant. Nucleos: 8
RAM Total: 3645.54 MB

========================================
Informacion de la aplicacion
========================================
Paquete: dev.mnovoa.SOA.vibecoding
PSS: 197.39 MB
Porcentaje RAM utilizada: 5.4145 %
CPU promedio: 0.00 %

========================================
```

## Analisis de resultados

En la medicion realizada, la version manual mostro un consumo de CPU alto aun en reposo. Esto indica que hay trabajo activo sostenido en la aplicacion, probablemente asociado a la vista 3D, actualizaciones de UI o tareas que no quedan suspendidas completamente. Con la pantalla de sensor abierta, el CPU promedio bajo a 62.50 %, manteniendo una memoria PSS cercana.

La version con vibecoding quedo estable en CPU durante las dos pruebas medidas con el script oficial. Su PSS fue levemente mayor que el de la version manual, pero el uso de RAM se mantuvo dentro del mismo orden. Para la entrega, el dato principal es que ambas APK fueron instaladas en el mismo telefono, medidas con el mismo script y comparadas bajo los cuatro escenarios solicitados.
