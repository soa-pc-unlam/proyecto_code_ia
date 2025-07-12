# 👻 Sistema de Monitoreo Paranormal

## Descripción

Este sistema simula un programa de monitoreo paranormal para una mansión embrujada. Los investigadores paranormales han sido convocados para investigar la mansión y este programa monitorea múltiples cámaras de última generación para reportar cualquier tipo de actividad extraña.

## 🏗️ Arquitectura del Sistema

### Zonas Monitoreadas
- **Sótano 2**
- **Ático**
- **Cocina**
- **Dormitorio**
- **Jardín**
- **Mausoleo**

### Eventos Detectables
- **Sin actividad** (60% probabilidad)
- **Movimiento detectado** (20% probabilidad)
- **Anomalía térmica** (10% probabilidad)
- **Sombra extraña** (7% probabilidad)
- **Ruido detectado** (3% probabilidad)

## 🚀 Cómo Ejecutar

### Requisitos
- Python 3.7 o superior
- Sistema operativo Windows

### Uso
```bash
python monitor_paranormal.py <duracion_segundos> <frecuencia_segundos>
```

### Ejemplos
```bash
# Monitoreo de 30 segundos con reportes cada 5 segundos
python monitor_paranormal.py 30 5

# Monitoreo de 60 segundos con reportes cada 3 segundos
python monitor_paranormal.py 60 3

# Monitoreo de 120 segundos con reportes cada 10 segundos
python monitor_paranormal.py 120 10
```

## 🔧 Características Técnicas

### Programación Concurrente
- Cada cámara es un **proceso independiente** usando `multiprocessing`
- El proceso principal espera a que todas las cámaras finalicen
- Comunicación entre procesos mediante `Manager().dict()`

### Generación de Eventos
- Eventos generados aleatoriamente con probabilidades predefinidas
- Cada cámara reporta eventos en intervalos regulares
- Contador de eventos paranormales por cámara

### Salida del Programa
- **Durante el monitoreo**: Reportes en tiempo real con timestamp
- **Al finalizar**: Resumen de eventos paranormales por cámara
- **Total general**: Suma de todos los eventos paranormales detectados

## 📊 Ejemplo de Salida

```
============================================================
👻 SISTEMA DE MONITOREO PARANORMAL INICIADO
============================================================
⏱️  Duración del monitoreo: 30 segundos
📡 Frecuencia de reportes: 5 segundos
📹 Cámaras activas: 6
============================================================
🔴 CÁMARA 1 iniciando monitoreo de Sótano 2
🔴 CÁMARA 2 iniciando monitoreo de Ático
🔴 CÁMARA 3 iniciando monitoreo de Cocina
🔴 CÁMARA 4 iniciando monitoreo de Dormitorio
🔴 CÁMARA 5 iniciando monitoreo de Jardín
🔴 CÁMARA 6 iniciando monitoreo de Mausoleo

[14:30:15] CÁMARA 1 | ZONA: Sótano 2 | EVENTO: Sin actividad
[14:30:15] CÁMARA 2 | ZONA: Ático | EVENTO: Movimiento detectado
[14:30:15] CÁMARA 3 | ZONA: Cocina | EVENTO: Sin actividad
[14:30:15] CÁMARA 4 | ZONA: Dormitorio | EVENTO: Sombra extraña
[14:30:15] CÁMARA 5 | ZONA: Jardín | EVENTO: Sin actividad
[14:30:15] CÁMARA 6 | ZONA: Mausoleo | EVENTO: Anomalía térmica

...

🛑 CÁMARA 1 finalizada. Eventos paranormales detectados: 2
🛑 CÁMARA 2 finalizada. Eventos paranormales detectados: 1
🛑 CÁMARA 3 finalizada. Eventos paranormales detectados: 0
🛑 CÁMARA 4 finalizada. Eventos paranormales detectados: 3
🛑 CÁMARA 5 finalizada. Eventos paranormales detectados: 1
🛑 CÁMARA 6 finalizada. Eventos paranormales detectados: 2

============================================================
📊 RESUMEN FINAL DEL MONITOREO
============================================================
CÁMARA 1 (Sótano 2): 2 eventos paranormales
CÁMARA 2 (Ático): 1 eventos paranormales
CÁMARA 3 (Cocina): 0 eventos paranormales
CÁMARA 4 (Dormitorio): 3 eventos paranormales
CÁMARA 5 (Jardín): 1 eventos paranormales
CÁMARA 6 (Mausoleo): 2 eventos paranormales
------------------------------------------------------------
🎯 TOTAL DE EVENTOS PARANORMALES: 9
⚠️  ¡ACTIVIDAD PARANORMAL DETECTADA! ¡Los investigadores deben investigar!
============================================================
```

## 🎯 Cumplimiento de Requisitos

✅ **Lenguaje Python** - Implementado en Python 3  
✅ **Plataforma Windows** - Compatible con Windows  
✅ **6 zonas monitoreadas** - Sótano 2, Ático, Cocina, Dormitorio, Jardín, Mausoleo  
✅ **Procesos independientes** - Cada cámara es un proceso separado  
✅ **5 tipos de eventos** - Todos los eventos especificados implementados  
✅ **Reportes por consola** - ID de cámara, zona y evento  
✅ **Parámetros configurables** - Duración y frecuencia como argumentos  
✅ **Contador de eventos** - Cada cámara cuenta eventos paranormales  
✅ **Espera de finalización** - Proceso principal espera a todas las cámaras  
✅ **Generación aleatoria** - Eventos generados con probabilidades realistas  

## 🔍 Detalles de Implementación

### Estructura de Clases
- `EventoParanormal`: Enum con todos los tipos de eventos
- `Camara`: Dataclass que representa una cámara individual
- `SistemaMonitoreoParanormal`: Clase principal que coordina todo el sistema

### Gestión de Procesos
- Uso de `multiprocessing.Process` para cada cámara
- `Manager().dict()` para compartir resultados entre procesos
- `join()` para esperar finalización de todos los procesos

### Probabilidades de Eventos
- **Sin actividad**: 60% (evento normal)
- **Movimiento detectado**: 20% (evento paranormal común)
- **Anomalía térmica**: 10% (evento paranormal moderado)
- **Sombra extraña**: 7% (evento paranormal raro)
- **Ruido detectado**: 3% (evento paranormal muy raro)

## 🐛 Solución de Problemas

### Error de Argumentos
Si recibes un error sobre argumentos incorrectos:
```bash
Uso: python monitor_paranormal.py <duracion_segundos> <frecuencia_segundos>
```

### Valores Negativos
Los parámetros deben ser números enteros positivos.

### Compatibilidad
El programa está diseñado para funcionar en Windows con Python 3.7+. 