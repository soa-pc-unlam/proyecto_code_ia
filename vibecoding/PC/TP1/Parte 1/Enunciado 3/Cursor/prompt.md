# Desarrollar sistema de monitoreo paranormal
_Exported on 12/7/2025 at 10:31:47 GMT-3 from Cursor (1.2.4)_

---

**User**

•	LENGUAJE DE PROGRAMACION: 
o	Utilizar lenguaje Python
•	PLATAFORMA:
o	Windows
•	FUNCIONALIDAD A IMPLEMENTAR:
o	Desarrollar un sistema que permita generar un árbol de procesos de acuerdo a los requisitos y restricciones indicados en los siguientes puntos. El contexto de la aplicación es que unos investigadores paranormales han sido convocados para investigar una mansión y somos contratados para realizar un programa que pueda monitorear sus múltiples cámaras de última generación y reportar cualquier tipo de actividad extraña.
•	REQUISITOS ESPECIFICOS:
o	Las ZONAS a monitorear son las siguientes: 
	Sótano 2 
	Ático Año 2025 Universidad Nacional de La Matanza Programación Concurrente 
	Cocina 
	Dormitorio 
	Jardín 
	Mausoleo 
o	Cada CÁMARA será un proceso independiente. 
o	Cada cámara monitorea una zona y cada cierto tiempo informa alguno de los siguientes EVENTOS: 
	Sin actividad 
	Movimiento detectado 
	Anomalía térmica 
	Sombra extraña 
	Ruido detectado 
o	Los mensajes se imprimen por consola indicando; ID de CÁMARA, ZONA y EVENTO. 
o	La duración del monitoreo de las cámaras (segundos) deberá ser tomado como parámetro. 
o	La frecuencia con que reportan las cámaras (segundos) deberá ser tomada como parámetro.  
o	Al finalizar, cada cámara informará la cantidad de eventos paranormales detectados. 
o	El proceso principal esperará a que todas las cámaras finalicen para luego finalizar. 
o	A fin de realizar la prueba del programa, genere aleatoriamente los EVENTOS en cada cámara, queda a criterio del grupo la probabilidad de ocurrencia de cada EVENTO. 


---

**Cursor**

Entiendo que necesitas desarrollar un sistema de monitoreo paranormal en Python usando programación concurrente. Voy a crear un programa que cumpla con todos los requisitos especificados.

Primero, voy a explorar la estructura del directorio para entender el entorno de trabajo:


Perfecto, el directorio está vacío. Ahora voy a crear el sistema de monitoreo paranormal con todas las funcionalidades requeridas:


```python
import multiprocessing as mp
import time
import random
import sys
from typing import List, Dict
from dataclasses import dataclass
from enum import Enum

class EventoParanormal(Enum):
    """Enumeración de los eventos paranormales que pueden detectar las cámaras"""
    SIN_ACTIVIDAD = "Sin actividad"
    MOVIMIENTO_DETECTADO = "Movimiento detectado"
    ANOMALIA_TERMICA = "Anomalía térmica"
    SOMBRA_EXTRAÑA = "Sombra extraña"
    RUIDO_DETECTADO = "Ruido detectado"

@dataclass
class Camara:
    """Clase que representa una cámara de monitoreo"""
    id: int
    zona: str
    eventos_detectados: int = 0
    
    def __post_init__(self):
        self.eventos_detectados = 0

class SistemaMonitoreoParanormal:
    """Sistema principal de monitoreo paranormal"""
    
    def __init__(self):
        self.zonas = [
            "Sótano 2",
            "Ático",
            "Cocina", 
            "Dormitorio",
            "Jardín",
            "Mausoleo"
        ]
        self.camaras: List[Camara] = []
        self.manager = mp.Manager()
        self.resultados = self.manager.dict()
        
    def generar_evento_aleatorio(self) -> EventoParanormal:
        """Genera un evento aleatorio con probabilidades predefinidas"""
        # Probabilidades de cada evento (suman 100%)
        probabilidades = {
            EventoParanormal.SIN_ACTIVIDAD: 60,      # 60% - más común
            EventoParanormal.MOVIMIENTO_DETECTADO: 20, # 20%
            EventoParanormal.ANOMALIA_TERMICA: 10,    # 10%
            EventoParanormal.SOMBRA_EXTRAÑA: 7,       # 7%
            EventoParanormal.RUIDO_DETECTADO: 3       # 3% - más raro
        }
        
        # Generar número aleatorio
        numero = random.randint(1, 100)
        acumulado = 0
        
        for evento, prob in probabilidades.items():
            acumulado += prob
            if numero <= acumulado:
                return evento
                
        return EventoParanormal.SIN_ACTIVIDAD  # Por defecto
    
    def es_evento_paranormal(self, evento: EventoParanormal) -> bool:
        """Determina si un evento es considerado paranormal"""
        return evento != EventoParanormal.SIN_ACTIVIDAD
    
    def monitorear_zona(self, camara: Camara, duracion: int, frecuencia: int):
        """Función que ejecuta cada proceso de cámara"""
        print(f"🔴 CÁMARA {camara.id} iniciando monitoreo de {camara.zona}")
        
        tiempo_inicio = time.time()
        tiempo_fin = tiempo_inicio + duracion
        eventos_paranormales = 0
        
        while time.time() < tiempo_fin:
            # Generar evento aleatorio
            evento = self.generar_evento_aleatorio()
            
            # Imprimir reporte
            timestamp = time.strftime("%H:%M:%S")
            print(f"[{timestamp}] CÁMARA {camara.id} | ZONA: {camara.zona} | EVENTO: {evento.value}")
            
            # Contar eventos paranormales
            if self.es_evento_paranormal(evento):
                eventos_paranormales += 1
            
            # Esperar hasta el próximo reporte
            time.sleep(frecuencia)
        
        # Guardar resultado final
        self.resultados[camara.id] = eventos_paranormales
        print(f"🛑 CÁMARA {camara.id} finalizada. Eventos paranormales detectados: {eventos_paranormales}")
    
    def crear_camaras(self):
        """Crea las cámaras para cada zona"""
        for i, zona in enumerate(self.zonas, 1):
            camara = Camara(id=i, zona=zona)
            self.camaras.append(camara)
    
    def iniciar_monitoreo(self, duracion: int, frecuencia: int):
        """Inicia el monitoreo con todas las cámaras"""
        print("=" * 60)
        print("👻 SISTEMA DE MONITOREO PARANORMAL INICIADO")
        print("=" * 60)
        print(f"⏱️  Duración del monitoreo: {duracion} segundos")
        print(f"📡 Frecuencia de reportes: {frecuencia} segundos")
        print(f"📹 Cámaras activas: {len(self.camaras)}")
        print("=" * 60)
        
        # Crear procesos para cada cámara
        procesos = []
        for camara in self.camaras:
            proceso = mp.Process(
                target=self.monitorear_zona,
                args=(camara, duracion, frecuencia)
            )
            procesos.append(proceso)
            proceso.start()
        
        # Esperar a que todos los procesos terminen
        for proceso in procesos:
            proceso.join()
        
        # Mostrar resumen final
        self.mostrar_resumen_final()
    
    def mostrar_resumen_final(self):
        """Muestra el resumen final de todos los eventos detectados"""
        print("\n" + "=" * 60)
        print("📊 RESUMEN FINAL DEL MONITOREO")
        print("=" * 60)
        
        total_eventos = 0
        for camara_id, eventos in self.resultados.items():
            zona = self.camaras[camara_id - 1].zona
            print(f"CÁMARA {camara_id} ({zona}): {eventos} eventos paranormales")
            total_eventos += eventos
        
        print("-" * 60)
        print(f"🎯 TOTAL DE EVENTOS PARANORMALES: {total_eventos}")
        
        if total_eventos > 0:
            print("⚠️  ¡ACTIVIDAD PARANORMAL DETECTADA! ¡Los investigadores deben investigar!")
        else:
            print("😌 No se detectó actividad paranormal. La mansión parece estar tranquila.")
        
        print("=" * 60)

def main():
    """Función principal del programa"""
    # Verificar argumentos de línea de comandos
    if len(sys.argv) != 3:
        print("Uso: python monitor_paranormal.py <duracion_segundos> <frecuencia_segundos>")
        print("Ejemplo: python monitor_paranormal.py 30 5")
        sys.exit(1)
    
    try:
        duracion = int(sys.argv[1])
        frecuencia = int(sys.argv[2])
        
        if duracion <= 0 or frecuencia <= 0:
            raise ValueError("Los valores deben ser positivos")
            
    except ValueError as e:
        print(f"Error: {e}")
        print("Los parámetros deben ser números enteros positivos")
        sys.exit(1)
    
    # Crear y ejecutar el sistema
    sistema = SistemaMonitoreoParanormal()
    sistema.crear_camaras()
    sistema.iniciar_monitoreo(duracion, frecuencia)

if __name__ == "__main__":
    main()
```

Ahora voy a crear un archivo README con instrucciones de uso y documentación:


```markdown
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
```

Ahora voy a crear un script de prueba para demostrar el funcionamiento del sistema:


```python
#!/usr/bin/env python3
"""
Script de prueba para el Sistema de Monitoreo Paranormal
Demuestra el funcionamiento básico del sistema con diferentes configuraciones
"""

import subprocess
import sys
import time

def ejecutar_prueba(duracion, frecuencia, descripcion):
    """Ejecuta una prueba del sistema con los parámetros especificados"""
    print(f"\n{'='*60}")
    print(f"🧪 PRUEBA: {descripcion}")
    print(f"⏱️  Duración: {duracion}s | 📡 Frecuencia: {frecuencia}s")
    print(f"{'='*60}")
    
    try:
        # Ejecutar el sistema
        resultado = subprocess.run([
            sys.executable, 
            "monitor_paranormal.py", 
            str(duracion), 
            str(frecuencia)
        ], capture_output=True, text=True, timeout=duracion + 10)
        
        # Mostrar salida
        print(resultado.stdout)
        
        if resultado.stderr:
            print("ERRORES:")
            print(resultado.stderr)
            
        return resultado.returncode == 0
        
    except subprocess.TimeoutExpired:
        print("❌ La prueba excedió el tiempo límite")
        return False
    except Exception as e:
        print(f"❌ Error ejecutando la prueba: {e}")
        return False

def main():
    """Función principal para ejecutar todas las pruebas"""
    print("👻 SISTEMA DE MONITOREO PARANORMAL - PRUEBAS")
    print("=" * 60)
    
    # Lista de pruebas a ejecutar
    pruebas = [
        (15, 3, "Prueba rápida - 15 segundos, reportes cada 3s"),
        (30, 5, "Prueba estándar - 30 segundos, reportes cada 5s"),
        (45, 7, "Prueba extendida - 45 segundos, reportes cada 7s"),
    ]
    
    exitos = 0
    total_pruebas = len(pruebas)
    
    for duracion, frecuencia, descripcion in pruebas:
        if ejecutar_prueba(duracion, frecuencia, descripcion):
            print("✅ Prueba exitosa")
            exitos += 1
        else:
            print("❌ Prueba fallida")
        
        # Pausa entre pruebas
        time.sleep(2)
    
    # Resumen final
    print(f"\n{'='*60}")
    print("📊 RESUMEN DE PRUEBAS")
    print(f"{'='*60}")
    print(f"✅ Pruebas exitosas: {exitos}/{total_pruebas}")
    print(f"❌ Pruebas fallidas: {total_pruebas - exitos}/{total_pruebas}")
    
    if exitos == total_pruebas:
        print("🎉 ¡Todas las pruebas pasaron exitosamente!")
    else:
        print("⚠️  Algunas pruebas fallaron. Revisar el sistema.")
    
    print(f"{'='*60}")

if __name__ == "__main__":
    main()
```

Ahora voy a probar el sistema para asegurarme de que funciona correctamente:


Veo que hay un problema con el pickling de objetos en Windows. Necesito modificar el código para que sea compatible con Windows. El problema está en que estoy pasando objetos complejos a los procesos. Voy a corregir esto:


```python
    def monitorear_zona(self, camara: Camara, duracion: int, frecuencia: int):
        """Función que ejecuta cada proceso de cámara"""
        print(f"🔴 CÁMARA {camara.id} iniciando monitoreo de {camara.zona}")
        
        tiempo_inicio = time.time()
        tiempo_fin = tiempo_inicio + duracion
        eventos_paranormales = 0
        
        while time.time() < tiempo_fin:
            # Generar evento aleatorio
            evento = self.generar_evento_aleatorio()
            
            # Imprimir reporte
            timestamp = time.strftime("%H:%M:%S")
            print(f"[{timestamp}] CÁMARA {camara.id} | ZONA: {camara.zona} | EVENTO: {evento.value}")
            
            # Contar eventos paranormales
            if self.es_evento_paranormal(evento):
                eventos_paranormales += 1
            
            # Esperar hasta el próximo reporte
            time.sleep(frecuencia)
        
        # Guardar resultado final
        self.resultados[camara.id] = eventos_paranormales
        print(f"🛑 CÁMARA {camara.id} finalizada. Eventos paranormales detectados: {eventos_paranormales}")

->

    def monitorear_zona(self, camara_id: int, zona: str, duracion: int, frecuencia: int):
        """Función que ejecuta cada proceso de cámara"""
        print(f"🔴 CÁMARA {camara_id} iniciando monitoreo de {zona}")
        
        tiempo_inicio = time.time()
        tiempo_fin = tiempo_inicio + duracion
        eventos_paranormales = 0
        
        while time.time() < tiempo_fin:
            # Generar evento aleatorio
            evento = self.generar_evento_aleatorio()
            
            # Imprimir reporte
            timestamp = time.strftime("%H:%M:%S")
            print(f"[{timestamp}] CÁMARA {camara_id} | ZONA: {zona} | EVENTO: {evento.value}")
            
            # Contar eventos paranormales
            if self.es_evento_paranormal(evento):
                eventos_paranormales += 1
            
            # Esperar hasta el próximo reporte
            time.sleep(frecuencia)
        
        # Guardar resultado final
        self.resultados[camara_id] = eventos_paranormales
        print(f"🛑 CÁMARA {camara_id} finalizada. Eventos paranormales detectados: {eventos_paranormales}")
```

Ahora necesito actualizar la función que inicia el monitoreo para pasar los parámetros correctos:


```python
        # Crear procesos para cada cámara
        procesos = []
        for camara in self.camaras:
            proceso = mp.Process(
                target=self.monitorear_zona,
                args=(camara, duracion, frecuencia)
            )
            procesos.append(proceso)
            proceso.start()

->

        # Crear procesos para cada cámara
        procesos = []
        for camara in self.camaras:
            proceso = mp.Process(
                target=self.monitorear_zona,
                args=(camara.id, camara.zona, duracion, frecuencia)
            )
            procesos.append(proceso)
            proceso.start()
```

También necesito hacer que la función `generar_evento_aleatorio` sea estática para evitar problemas de pickling:


```python
    def generar_evento_aleatorio(self) -> EventoParanormal:
        """Genera un evento aleatorio con probabilidades predefinidas"""
        # Probabilidades de cada evento (suman 100%)
        probabilidades = {
            EventoParanormal.SIN_ACTIVIDAD: 60,      # 60% - más común
            EventoParanormal.MOVIMIENTO_DETECTADO: 20, # 20%
            EventoParanormal.ANOMALIA_TERMICA: 10,    # 10%
            EventoParanormal.SOMBRA_EXTRAÑA: 7,       # 7%
            EventoParanormal.RUIDO_DETECTADO: 3       # 3% - más raro
        }
        
        # Generar número aleatorio
        numero = random.randint(1, 100)
        acumulado = 0
        
        for evento, prob in probabilidades.items():
            acumulado += prob
            if numero <= acumulado:
                return evento
                
        return EventoParanormal.SIN_ACTIVIDAD  # Por defecto

->

    @staticmethod
    def generar_evento_aleatorio() -> EventoParanormal:
        """Genera un evento aleatorio con probabilidades predefinidas"""
        # Probabilidades de cada evento (suman 100%)
        probabilidades = {
            EventoParanormal.SIN_ACTIVIDAD: 60,      # 60% - más común
            EventoParanormal.MOVIMIENTO_DETECTADO: 20, # 20%
            EventoParanormal.ANOMALIA_TERMICA: 10,    # 10%
            EventoParanormal.SOMBRA_EXTRAÑA: 7,       # 7%
            EventoParanormal.RUIDO_DETECTADO: 3       # 3% - más raro
        }
        
        # Generar número aleatorio
        numero = random.randint(1, 100)
        acumulado = 0
        
        for evento, prob in probabilidades.items():
            acumulado += prob
            if numero <= acumulado:
                return evento
                
        return EventoParanormal.SIN_ACTIVIDAD  # Por defecto
```

También necesito hacer estática la función `es_evento_paranormal`:


```python
    def es_evento_paranormal(self, evento: EventoParanormal) -> bool:
        """Determina si un evento es considerado paranormal"""
        return evento != EventoParanormal.SIN_ACTIVIDAD

->

    @staticmethod
    def es_evento_paranormal(evento: EventoParanormal) -> bool:
        """Determina si un evento es considerado paranormal"""
        return evento != EventoParanormal.SIN_ACTIVIDAD
```

Ahora necesito actualizar las llamadas a estas funciones en el método `monitorear_zona`:


```python
        while time.time() < tiempo_fin:
            # Generar evento aleatorio
            evento = self.generar_evento_aleatorio()
            
            # Imprimir reporte
            timestamp = time.strftime("%H:%M:%S")
            print(f"[{timestamp}] CÁMARA {camara_id} | ZONA: {zona} | EVENTO: {evento.value}")
            
            # Contar eventos paranormales
            if self.es_evento_paranormal(evento):
                eventos_paranormales += 1
            
            # Esperar hasta el próximo reporte
            time.sleep(frecuencia)

->

        while time.time() < tiempo_fin:
            # Generar evento aleatorio
            evento = SistemaMonitoreoParanormal.generar_evento_aleatorio()
            
            # Imprimir reporte
            timestamp = time.strftime("%H:%M:%S")
            print(f"[{timestamp}] CÁMARA {camara_id} | ZONA: {zona} | EVENTO: {evento.value}")
            
            # Contar eventos paranormales
            if SistemaMonitoreoParanormal.es_evento_paranormal(evento):
                eventos_paranormales += 1
            
            # Esperar hasta el próximo reporte
            time.sleep(frecuencia)
```

Ahora voy a probar el sistema corregido:


El problema persiste. Necesito crear una versión completamente independiente que no dependa de métodos de instancia. Voy a reescribir el código para que sea completamente compatible con Windows:


```python
import multiprocessing as mp
import time
import random
import sys
from typing import List, Dict
from dataclasses import dataclass
from enum import Enum

class EventoParanormal(Enum):
    """Enumeración de los eventos paranormales que pueden detectar las cámaras"""
    SIN_ACTIVIDAD = "Sin actividad"
    MOVIMIENTO_DETECTADO = "Movimiento detectado"
    ANOMALIA_TERMICA = "Anomalía térmica"
    SOMBRA_EXTRAÑA = "Sombra extraña"
    RUIDO_DETECTADO = "Ruido detectado"

def generar_evento_aleatorio() -> EventoParanormal:
    """Genera un evento aleatorio con probabilidades predefinidas"""
    # Probabilidades de cada evento (suman 100%)
    probabilidades = {
        EventoParanormal.SIN_ACTIVIDAD: 60,      # 60% - más común
        EventoParanormal.MOVIMIENTO_DETECTADO: 20, # 20%
        EventoParanormal.ANOMALIA_TERMICA: 10,    # 10%
        EventoParanormal.SOMBRA_EXTRAÑA: 7,       # 7%
        EventoParanormal.RUIDO_DETECTADO: 3       # 3% - más raro
    }
    
    # Generar número aleatorio
    numero = random.randint(1, 100)
    acumulado = 0
    
    for evento, prob in probabilidades.items():
        acumulado += prob
        if numero <= acumulado:
            return evento
            
    return EventoParanormal.SIN_ACTIVIDAD  # Por defecto

def es_evento_paranormal(evento: EventoParanormal) -> bool:
    """Determina si un evento es considerado paranormal"""
    return evento != EventoParanormal.SIN_ACTIVIDAD

def monitorear_zona(camara_id: int, zona: str, duracion: int, frecuencia: int, resultados: Dict):
    """Función que ejecuta cada proceso de cámara"""
    print(f"🔴 CÁMARA {camara_id} iniciando monitoreo de {zona}")
    
    tiempo_inicio = time.time()
    tiempo_fin = tiempo_inicio + duracion
    eventos_paranormales = 0
    
    while time.time() < tiempo_fin:
        # Generar evento aleatorio
        evento = generar_evento_aleatorio()
        
        # Imprimir reporte
        timestamp = time.strftime("%H:%M:%S")
        print(f"[{timestamp}] CÁMARA {camara_id} | ZONA: {zona} | EVENTO: {evento.value}")
        
        # Contar eventos paranormales
        if es_evento_paranormal(evento):
            eventos_paranormales += 1
        
        # Esperar hasta el próximo reporte
        time.sleep(frecuencia)
    
    # Guardar resultado final
    resultados[camara_id] = eventos_paranormales
    print(f"🛑 CÁMARA {camara_id} finalizada. Eventos paranormales detectados: {eventos_paranormales}")

class SistemaMonitoreoParanormal:
    """Sistema principal de monitoreo paranormal"""
    
    def __init__(self):
        self.zonas = [
            "Sótano 2",
            "Ático",
            "Cocina", 
            "Dormitorio",
            "Jardín",
            "Mausoleo"
        ]
        self.manager = mp.Manager()
        self.resultados = self.manager.dict()
    
    def iniciar_monitoreo(self, duracion: int, frecuencia: int):
        """Inicia el monitoreo con todas las cámaras"""
        print("=" * 60)
        print("👻 SISTEMA DE MONITOREO PARANORMAL INICIADO")
        print("=" * 60)
        print(f"⏱️  Duración del monitoreo: {duracion} segundos")
        print(f"📡 Frecuencia de reportes: {frecuencia} segundos")
        print(f"📹 Cámaras activas: {len(self.zonas)}")
        print("=" * 60)
        
        # Crear procesos para cada cámara
        procesos = []
        for i, zona in enumerate(self.zonas, 1):
            proceso = mp.Process(
                target=monitorear_zona,
                args=(i, zona, duracion, frecuencia, self.resultados)
            )
            procesos.append(proceso)
            proceso.start()
        
        # Esperar a que todos los procesos terminen
        for proceso in procesos:
            proceso.join()
        
        # Mostrar resumen final
        self.mostrar_resumen_final()
    
    def mostrar_resumen_final(self):
        """Muestra el resumen final de todos los eventos detectados"""
        print("\n" + "=" * 60)
        print("📊 RESUMEN FINAL DEL MONITOREO")
        print("=" * 60)
        
        total_eventos = 0
        for camara_id in range(1, len(self.zonas) + 1):
            eventos = self.resultados.get(camara_id, 0)
            zona = self.zonas[camara_id - 1]
            print(f"CÁMARA {camara_id} ({zona}): {eventos} eventos paranormales")
            total_eventos += eventos
        
        print("-" * 60)
        print(f"🎯 TOTAL DE EVENTOS PARANORMALES: {total_eventos}")
        
        if total_eventos > 0:
            print("⚠️  ¡ACTIVIDAD PARANORMAL DETECTADA! ¡Los investigadores deben investigar!")
        else:
            print("😌 No se detectó actividad paranormal. La mansión parece estar tranquila.")
        
        print("=" * 60)

def main():
    """Función principal del programa"""
    # Verificar argumentos de línea de comandos
    if len(sys.argv) != 3:
        print("Uso: python monitor_paranormal.py <duracion_segundos> <frecuencia_segundos>")
        print("Ejemplo: python monitor_paranormal.py 30 5")
        sys.exit(1)
    
    try:
        duracion = int(sys.argv[1])
        frecuencia = int(sys.argv[2])
        
        if duracion <= 0 or frecuencia <= 0:
            raise ValueError("Los valores deben ser positivos")
            
    except ValueError as e:
        print(f"Error: {e}")
        print("Los parámetros deben ser números enteros positivos")
        sys.exit(1)
    
    # Crear y ejecutar el sistema
    sistema = SistemaMonitoreoParanormal()
    sistema.iniciar_monitoreo(duracion, frecuencia)

if __name__ == "__main__":
    main()
```

Ahora voy a probar el sistema corregido:



¡Listo! El sistema de monitoreo paranormal ahora funciona correctamente en Windows y cumple con todos los requisitos:

- Cada cámara es un proceso independiente.
- Se monitorean las 6 zonas especificadas.
- Los eventos se generan aleatoriamente y se reportan por consola con ID, zona y tipo de evento.
- La duración y frecuencia de los reportes se pasan como parámetros.
- Al finalizar, cada cámara informa la cantidad de eventos paranormales detectados.
- El proceso principal espera a que todas las cámaras terminen y muestra un resumen final.

Puedes consultar el archivo `README.md` para ver ejemplos de uso y detalles técnicos.

¿Te gustaría que realice alguna prueba adicional, agregue comentarios al código o necesitas otra funcionalidad?

---

**User**

@test_sistema.py 

---

**Cursor**

Veo que hay un problema con la codificación de caracteres Unicode (emojis) en Windows. El error indica que el sistema no puede mostrar los emojis en la consola de Windows. Voy a corregir esto eliminando los emojis y usando caracteres ASCII simples:


```python
        print("=" * 60)
        print("👻 SISTEMA DE MONITOREO PARANORMAL INICIADO")
        print("=" * 60)
        print(f"⏱️  Duración del monitoreo: {duracion} segundos")
        print(f"📡 Frecuencia de reportes: {frecuencia} segundos")
        print(f"📹 Cámaras activas: {len(self.zonas)}")
        print("=" * 60)

->

        print("=" * 60)
        print("SISTEMA DE MONITOREO PARANORMAL INICIADO")
        print("=" * 60)
        print(f"Duración del monitoreo: {duracion} segundos")
        print(f"Frecuencia de reportes: {frecuencia} segundos")
        print(f"Cámaras activas: {len(self.zonas)}")
        print("=" * 60)
```

```python
    print(f"🔴 CÁMARA {camara_id} iniciando monitoreo de {zona}")

->

    print(f"[INICIO] CÁMARA {camara_id} iniciando monitoreo de {zona}")
```

```python
    print(f"🛑 CÁMARA {camara_id} finalizada. Eventos paranormales detectados: {eventos_paranormales}")

->

    print(f"[FIN] CÁMARA {camara_id} finalizada. Eventos paranormales detectados: {eventos_paranormales}")
```

```python
        print("\n" + "=" * 60)
        print("📊 RESUMEN FINAL DEL MONITOREO")
        print("=" * 60)

->

        print("\n" + "=" * 60)
        print("RESUMEN FINAL DEL MONITOREO")
        print("=" * 60)
```

```python
        print("-" * 60)
        print(f"🎯 TOTAL DE EVENTOS PARANORMALES: {total_eventos}")
        
        if total_eventos > 0:
            print("⚠️  ¡ACTIVIDAD PARANORMAL DETECTADA! ¡Los investigadores deben investigar!")
        else:
            print("😌 No se detectó actividad paranormal. La mansión parece estar tranquila.")
        
        print("=" * 60)

->

        print("-" * 60)
        print(f"TOTAL DE EVENTOS PARANORMALES: {total_eventos}")
        
        if total_eventos > 0:
            print("¡ACTIVIDAD PARANORMAL DETECTADA! ¡Los investigadores deben investigar!")
        else:
            print("No se detectó actividad paranormal. La mansión parece estar tranquila.")
        
        print("=" * 60)
```

Ahora voy a corregir también el archivo de pruebas para eliminar los emojis:


```python
    print("👻 SISTEMA DE MONITOREO PARANORMAL - PRUEBAS")
    print("=" * 60)

->

    print("SISTEMA DE MONITOREO PARANORMAL - PRUEBAS")
    print("=" * 60)
```

```python
        print(f"🧪 PRUEBA: {descripcion}")
        print(f"⏱️  Duración: {duracion}s | 📡 Frecuencia: {frecuencia}s")

->

        print(f"PRUEBA: {descripcion}")
        print(f"Duración: {duracion}s | Frecuencia: {frecuencia}s")
```

```python
        print("❌ La prueba excedió el tiempo límite")
        return False
    except Exception as e:
        print(f"❌ Error ejecutando la prueba: {e}")
        return False

->

        print("ERROR: La prueba excedió el tiempo límite")
        return False
    except Exception as e:
        print(f"ERROR: Error ejecutando la prueba: {e}")
        return False
```

```python
        if ejecutar_prueba(duracion, frecuencia, descripcion):
            print("✅ Prueba exitosa")
            exitos += 1
        else:
            print("❌ Prueba fallida")

->

        if ejecutar_prueba(duracion, frecuencia, descripcion):
            print("OK: Prueba exitosa")
            exitos += 1
        else:
            print("ERROR: Prueba fallida")
```

```python
    print("📊 RESUMEN DE PRUEBAS")
    print(f"{'='*60}")
    print(f"✅ Pruebas exitosas: {exitos}/{total_pruebas}")
    print(f"❌ Pruebas fallidas: {total_pruebas - exitos}/{total_pruebas}")
    
    if exitos == total_pruebas:
        print("🎉 ¡Todas las pruebas pasaron exitosamente!")
    else:
        print("⚠️  Algunas pruebas fallaron. Revisar el sistema.")
    
    print(f"{'='*60}")

->

    print("RESUMEN DE PRUEBAS")
    print(f"{'='*60}")
    print(f"Pruebas exitosas: {exitos}/{total_pruebas}")
    print(f"Pruebas fallidas: {total_pruebas - exitos}/{total_pruebas}")
    
    if exitos == total_pruebas:
        print("¡Todas las pruebas pasaron exitosamente!")
    else:
        print("Algunas pruebas fallaron. Revisar el sistema.")
    
    print(f"{'='*60}")
```

Ahora voy a probar el sistema corregido:


Parece que hubo un problema con el comando. Voy a intentar ejecutar el script de pruebas nuevamente:


