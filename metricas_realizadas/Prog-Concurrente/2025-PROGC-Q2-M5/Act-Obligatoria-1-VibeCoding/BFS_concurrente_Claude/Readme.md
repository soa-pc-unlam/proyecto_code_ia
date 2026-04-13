# Algoritmo BFS Concurrente en Java 17

## 📋 Descripción del Proyecto

Este proyecto implementa el algoritmo de búsqueda en anchura (BFS - Breadth-First Search) utilizando **concurrencia** en Java 17. El algoritmo explora un grafo en paralelo usando múltiples threads, manteniendo las características fundamentales de BFS.

## 🏗️ Estructura del Proyecto

```
proyecto-bfs-concurrente/
├── Grafo.java                 # Estructura de datos del grafo
├── BFSConcurrente.java        # Implementación del algoritmo BFS paralelo
├── Main.java                  # Lotes de prueba y validación
└── README.md                  # Esta guía
```

## 🔧 Compilación y Ejecución

### Requisitos
- **Java 17** o superior
- Sistema operativo: Windows (compatible con otros SO)

### Compilar

```bash
javac Grafo.java BFSConcurrente.java Main.java
```

### Ejecutar

```bash
java Main
```

## 🎯 Características Principales

### 1. Clase Grafo
- Representa un grafo dirigido usando **listas de adyacencia**
- Utiliza `ConcurrentHashMap` para operaciones thread-safe
- Soporta grafos dirigidos y no dirigidos

### 2. Algoritmo BFS Concurrente

#### Herramientas de Concurrencia Utilizadas:

**a) Estructuras Thread-Safe:**
- `ConcurrentHashMap.newKeySet()`: Para el conjunto de nodos visitados
- `ConcurrentLinkedQueue`: Cola de nodos a procesar
- `Collections.synchronizedList()`: Lista para el orden de visita

**b) Mecanismos de Sincronización:**
- `AtomicInteger`: Contador de nodos en proceso (evita condiciones de carrera)
- `Object lock`: Para sincronizar las salidas por consola
- `ExecutorService`: Pool de threads para paralelizar el trabajo

**c) Control de Concurrencia:**
- Operaciones atómicas con `visitados.add()` para marcar nodos
- Sincronización de bloques críticos con `synchronized`
- Control de terminación basado en contador atómico

#### Funcionamiento:
1. Se crea un pool de threads workers
2. Cada thread toma nodos de la cola compartida
3. Los threads marcan nodos como visitados de forma atómica
4. Los vecinos no visitados se agregan a la cola
5. El algoritmo termina cuando no hay más nodos en proceso

## 🧪 Lotes de Prueba

### Lote de Prueba 1: Grafo tipo Árbol
```
        0
      / | \
     1  2  3
    / \    |
   4   5   6
       |
       7
```
- **Nodos:** 8
- **Threads:** 3
- **Características:** Estructura jerárquica sin ciclos

### Lote de Prueba 2: Grafo con Ciclos
```
        0 ←→ 1
       ↙ ↘   ↓
      2 ←→ 3 ← 4
      ↓    ↓
      5 → 6 ← 7
          ↓
          8
```
- **Nodos:** 9
- **Threads:** 4
- **Características:** Múltiples ciclos y caminos alternativos

## ✅ Validaciones Implementadas

Cada lote de prueba incluye validaciones automáticas que verifican:

1. **No hay duplicados:** Cada nodo se visita una sola vez
2. **Orden por niveles:** Se respeta la propiedad fundamental de BFS
3. **Inicio correcto:** El primer nodo visitado es el inicial
4. **Alcanzabilidad:** Se detectan componentes no conectadas

## 📊 Salida Esperada

El programa muestra:
- Estructura del grafo (listas de adyacencia)
- Proceso de exploración con identificación de threads
- Orden de visita resultante
- Validaciones del resultado
- Niveles esperados por BFS

### Ejemplo de Salida:
```
[Thread-0] Procesando vértice: 0
[Thread-0] Descubriendo vecino: 1
[Thread-1] Descubriendo vecino: 2
[Thread-2] Descubriendo vecino: 3
...
Orden de visita: 0 -> 1 -> 2 -> 3 -> 4 -> 5 -> 6 -> 7
```

## 🔐 Aspectos de Seguridad Thread-Safe

### Variables Compartidas Protegidas:
- `visitados`: Set concurrente para evitar visitas duplicadas
- `cola`: Cola concurrente para distribución de trabajo
- `ordenVisita`: Lista sincronizada para resultados
- `nodosEnProceso`: Contador atómico para control de terminación

### Sincronización:
- Marcado atómico de nodos visitados
- Locks para impresión ordenada
- Espera activa con verificación de condiciones

## 📈 Ventajas de la Implementación Concurrente

1. **Mayor rendimiento:** En grafos grandes, múltiples nodos del mismo nivel se procesan simultáneamente
2. **Escalabilidad:** El número de threads es configurable
3. **Thread-safety:** Evita condiciones de carrera y garantiza consistencia
4. **Corrección:** Mantiene las propiedades de BFS (orden por niveles)

## 🎓 Conceptos de Concurrencia Aplicados

- **Paralelismo de datos:** Múltiples threads procesando diferentes nodos
- **Variables atómicas:** Operaciones sin locks para contadores
- **Colecciones concurrentes:** Estructuras optimizadas para acceso paralelo
- **Sincronización explícita:** Locks para secciones críticas
- **Gestión de threads:** ExecutorService y thread pools

## 📝 Notas Importantes

- El orden **dentro de cada nivel** puede variar debido a la concurrencia (esto es normal)
- El orden **entre niveles** siempre se respeta (nivel N antes que N+1)
- El algoritmo garantiza que cada nodo se visita exactamente una vez
- La validación automática verifica la corrección del resultado

## 🚀 Posibles Extensiones

1. Implementar BFS bidireccional concurrente
2. Agregar métricas de rendimiento (tiempo de ejecución)
3. Visualización gráfica del proceso de exploración
4. Comparación con BFS secuencial
5. Implementar otros algoritmos de grafos (DFS, Dijkstra) con concurrencia

---

**Autor:** Proyecto Educativo - Java 17 Concurrency
**Versión:** 1.0
**Java:** 17+