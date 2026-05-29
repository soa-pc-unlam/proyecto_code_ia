# Cómo reproducir las mediciones

Guía corta para volver a correr las herramientas usadas en el informe.
Las herramientas se instalan dentro de `Metricas/tools/` que está
ignorado por git, así que cada quien las instala localmente.

## Requisitos previos

- **JDK 17+** (probado con Amazon Corretto 20). Verificar con `java -version`.
- **Python 3.10+**. Verificar con `python3 --version`.
- **curl + unzip** (vienen en macOS/Linux por default).

## Setup inicial (una sola vez)

Desde la raíz del repo:

```bash
mkdir -p Metricas/tools && cd Metricas/tools

# 1) Lizard + matplotlib en un venv aislado
python3 -m venv venv
./venv/bin/pip install lizard matplotlib

# 2) SpotBugs
curl -sL -o spotbugs.zip https://github.com/spotbugs/spotbugs/releases/download/4.8.6/spotbugs-4.8.6.zip
unzip -q spotbugs.zip && rm spotbugs.zip && mv spotbugs-4.8.6 spotbugs
```

---

## Herramientas y comandos

### 1. Lizard — complejidad ciclomática y NLOC
**Qué mide:** Cyclomatic Complexity (CCN), líneas de código no vacías
(NLOC), conteo de tokens y parámetros por función.
**Output:** `Metricas/mediciones/ciclomatica/lizard-report.{txt,csv}`

```bash
cd /ruta/al/repo
./Metricas/tools/venv/bin/lizard TP-Integrador/Sheriffsss/src \
  --languages java \
  -o Metricas/mediciones/ciclomatica/lizard-report.txt

./Metricas/tools/venv/bin/lizard TP-Integrador/Sheriffsss/src \
  --languages java --csv \
  > Metricas/mediciones/ciclomatica/lizard-report.csv
```

---

### 2. Maintainability Index — script propio
**Qué mide:** MI por archivo (variante de Microsoft 0–100) a partir del
CSV de Lizard.
**Output:** `Metricas/mediciones/mantenibilidad/mi-report.txt`

```bash
./Metricas/tools/venv/bin/python Metricas/tools/compute_mi.py \
  Metricas/mediciones/ciclomatica/lizard-report.csv \
  | tee Metricas/mediciones/mantenibilidad/mi-report.txt
```

> Requiere haber corrido Lizard antes (depende del CSV).

---

### 3. SpotBugs — análisis estático de bugs
**Qué mide:** defectos detectables estáticamente, incluida la categoría
`MT_CORRECTNESS` (concurrencia: deadlocks, sincronización inconsistente,
locks no liberados, etc.).
**Output:** HTML + XML en `Metricas/mediciones/spotbugs/`

```bash
# Compilar primero (genera los .class)
cd TP-Integrador/Sheriffsss
rm -rf out && mkdir out
javac -encoding UTF-8 -d out $(find src -name "*.java")
cd ../..

# Correr SpotBugs sobre los .class
java -jar Metricas/tools/spotbugs/lib/spotbugs.jar -textui -effort:max \
  -html -output Metricas/mediciones/spotbugs/spotbugs-report.html \
  TP-Integrador/Sheriffsss/out

java -jar Metricas/tools/spotbugs/lib/spotbugs.jar -textui -effort:max \
  -xml -output Metricas/mediciones/spotbugs/spotbugs-report.xml \
  TP-Integrador/Sheriffsss/out
```

---

### 4. jcmd — threads y heap en tiempo de ejecución
**Qué mide:** dump de todos los hilos vivos + estado del heap del GC.
También detecta deadlocks automáticamente.
**Output:** `Metricas/mediciones/runtime/threads-*.txt` y `heap-*.txt`

Para tomar snapshots necesitás el juego corriendo y su PID. Lo más
práctico es lanzar + medir en una sola sesión de bash:

```bash
cd TP-Integrador/Sheriffsss
java -cp "out:resources" SheriffsssPackage.Main &
PID=$!
sleep 5   # esperar al menú
jcmd $PID Thread.print > ../../Metricas/mediciones/runtime/threads-t5.txt
jcmd $PID GC.heap_info > ../../Metricas/mediciones/runtime/heap-t5.txt
# ... más snapshots si querés
kill $PID
```

> En macOS con Corretto 20 puede crashear el JVM a los ~15s por un bug
> nativo (`caulk`). Tomar snapshots tempranos o usar `-Xint`.

---

### 5. ps — CPU y memoria del proceso
**Qué mide:** CPU%, %MEM, RSS (memoria residente), VSZ, tiempo
transcurrido.
**Output:** `Metricas/mediciones/runtime/ps-*.txt`

Mismo flujo que jcmd, agregando:
```bash
ps -p $PID -o pid,pcpu,pmem,rss,vsz,etime,stat \
  > ../../Metricas/mediciones/runtime/ps-t5.txt
```

---

### 6. make_charts.py — gráficos del informe
**Qué genera:** los 5 PNG del informe, leyendo los outputs anteriores.
**Output:** `Metricas/informe/img/0[1-5]-*.png`

```bash
./Metricas/tools/venv/bin/python Metricas/tools/make_charts.py
```

> Requiere haber corrido Lizard, SpotBugs y al menos un snapshot de
> runtime con etiquetas `10-jit-t5`, `11-jit-t10`, `12-jit-t14`. Si
> nombrás los snapshots distinto, editá las rutas dentro del script.

---

## Resumen rápido

| Herramienta | Mide | Cómo correrla |
|---|---|---|
| Lizard | CCN, NLOC, tokens | `lizard src --languages java --csv` |
| `compute_mi.py` | Maintainability Index | `python compute_mi.py lizard.csv` |
| SpotBugs | Bugs estáticos + MT_CORRECTNESS | `java -jar spotbugs.jar -textui -html ... out/` |
| `jcmd Thread.print` | Hilos vivos, deadlocks | `jcmd <pid> Thread.print` |
| `jcmd GC.heap_info` | Heap del GC | `jcmd <pid> GC.heap_info` |
| `ps` | CPU%, RSS | `ps -p <pid> -o pcpu,rss,...` |
| `make_charts.py` | Gráficos PNG | `python make_charts.py` |
