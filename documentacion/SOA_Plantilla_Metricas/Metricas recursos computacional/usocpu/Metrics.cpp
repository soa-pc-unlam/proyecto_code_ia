#include <Arduino.h>   // Serial, ESP.*, psramFound(), etc.
extern "C" {
  #include "freertos/FreeRTOS.h"
  #include "freertos/task.h"
  #include "freertos/semphr.h"
}
#include <cstdint>     // uint32_t, uint64_t
#include <cstddef>     // size_t, NULL
#include <cstring>     // strncmp

// ------------------ Forward declarations ------------------
static void StatsCpu();
static void printMemoryStats();
static void StatsTask(void *pv);

// ------------------ Estado global (solo acá, no en .h) ----
static TaskHandle_t gStatsTask = nullptr;

// ------------------ Implementación ------------------------
static void StatsCpu() {
#if (configGENERATE_RUN_TIME_STATS == 1)
  UBaseType_t maxTasks = uxTaskGetNumberOfTasks();
  TaskStatus_t *taskArray = (TaskStatus_t*) pvPortMalloc(maxTasks * sizeof(TaskStatus_t));
  if (!taskArray) {
    Serial.println("[Stats] Error de memoria");
    return;
  }

  unsigned long totalRuntime = 0;
  UBaseType_t taskCount = uxTaskGetSystemState(taskArray, maxTasks, &totalRuntime);

  uint64_t totalCore[2] = {0, 0};
  uint64_t idleCore[2]  = {0, 0};

  // Acumulo tiempos por core y detecto IDLE0/IDLE1
  for (UBaseType_t i = 0; i < taskCount; i++) {
    if (taskArray[i].xCoreID < 2) {
      totalCore[taskArray[i].xCoreID] += taskArray[i].ulRunTimeCounter;

      const char* name = taskArray[i].pcTaskName;
      if (name && (strncmp(name, "IDLE0", 5) == 0 || strncmp(name, "IDLE1", 5) == 0)) {
        idleCore[taskArray[i].xCoreID] += taskArray[i].ulRunTimeCounter;
      }
    }
  }

  Serial.println("\n=== Uso de CPU por núcleo y tarea ===");
  for (UBaseType_t i = 0; i < taskCount; i++) {
    uint32_t core = taskArray[i].xCoreID;
    if (core < 2 && totalCore[core] > 0) {
      float pct = 100.0f * (float)taskArray[i].ulRunTimeCounter / (float)totalCore[core];
      Serial.printf("Core %u | %-16s | %6.2f%%\n", core, taskArray[i].pcTaskName, pct);
      vTaskDelay(1);
    }
  }

  Serial.println("\n=== Contribución al total del sistema ===");
  if (totalRuntime > 0) {
    for (int core = 0; core < 2; core++) {
      float pctCoreTotal = 100.0f * (float)totalCore[core] / (float)totalRuntime;
      float pctIdleTotal = 100.0f * (float)idleCore[core]  / (float)totalRuntime;
      float pctBusyTotal = pctCoreTotal - pctIdleTotal;
      Serial.printf(
        "Core %d -> Total: %6.2f%% | Ocupado: %6.2f%% | Libre (IDLE): %6.2f%%\n",
        core, pctCoreTotal, pctBusyTotal, pctIdleTotal
      );
    }
  } else {
    Serial.println("(totalRuntime = 0: aún no hay muestras suficientes)");
  }

  vPortFree(taskArray);
#else
  // Si no activaste las stats en FreeRTOS, avisamos para evitar confusión
  Serial.println("\n=== Stats de CPU no disponibles ===");
  Serial.println("Habilita configGENERATE_RUN_TIME_STATS=1 en FreeRTOSConfig.h");
#endif
}

static void printMemoryStats() {
  Serial.println("\n=== Estado de la memoria en ESP32 ===");
  Serial.println("=== Memoria interna (Heap) ===");
  size_t heapTotal = ESP.getHeapSize();
  size_t heapLibre = ESP.getFreeHeap();
  size_t heapUsado = heapTotal - heapLibre;

  Serial.printf("Heap total : %u bytes\n", (unsigned)heapTotal);
  Serial.printf("Heap libre : %u bytes\n", (unsigned)heapLibre);
  Serial.printf("Heap usado : %u bytes\n", (unsigned)heapUsado);
  Serial.printf("Uso        : %.2f %%\n\n",
                heapTotal ? (heapUsado * 100.0) / heapTotal : 0.0);

  if (psramFound()) {
    Serial.println("=== Memoria externa (PSRAM) ===");
    size_t psramTotal = ESP.getPsramSize();
    size_t psramLibre = ESP.getFreePsram();
    size_t psramUsado = psramTotal - psramLibre;

    Serial.printf("PSRAM total: %u bytes\n", (unsigned)psramTotal);
    Serial.printf("PSRAM libre: %u bytes\n", (unsigned)psramLibre);
    Serial.printf("PSRAM usado: %u bytes\n", (unsigned)psramUsado);
    Serial.printf("Uso        : %.2f %%\n\n",
                  psramTotal ? (psramUsado * 100.0) / psramTotal : 0.0);
  } else {
    Serial.println("No se detectó PSRAM en este módulo.");
  }
}

static void StatsTask(void *pv) {
  (void)pv;
  for (;;) {
    StatsCpu();
    printMemoryStats();

    // Espera de 5 segundos entre impresiones
    vTaskDelay(pdMS_TO_TICKS(5000));
  }
}
// === AÑADIDOS PARA SALIDA CSV ===
static void printMetricsCSVHeader();
static void printMetricsCSV();        // imprime todas las filas de una muestra
static void StatsTaskCSV(void *pv);   // tarea que emite CSV cada 5s

// Imprime un string entre comillas y escapando comillas dobles (CSV-safe)
static void csvPrintQuoted(const char* s) {
  Serial.write('"');
  if (s) {
    while (*s) {
      if (*s == '"') { Serial.print("\"\""); } else { Serial.write(*s); }
      ++s;
    }
  }
  Serial.write('"');
}

static void printMetricsCSVHeader() {
  Serial.println(
    "ts_ms,kind,core,task,pct_of_core,core_total_pct,busy_pct,idle_pct,"
    "run_counter,core_runtime,total_runtime,"
    "heap_total,heap_free,heap_used,heap_pct,"
    "psram_total,psram_free,psram_used,psram_pct"
  );
}
static void printMetricsCSV() {
  const unsigned long ts = millis();

#if (configGENERATE_RUN_TIME_STATS == 1)
  { // scope interno: evita problemas de "crosses initialization"
    UBaseType_t maxTasks = uxTaskGetNumberOfTasks();
    TaskStatus_t *taskArray = (TaskStatus_t*) pvPortMalloc(maxTasks * sizeof(TaskStatus_t));
    if (taskArray) {
      unsigned long totalRuntime = 0;
      UBaseType_t taskCount = uxTaskGetSystemState(taskArray, maxTasks, &totalRuntime);

      uint64_t totalCore[2] = {0, 0};
      uint64_t idleCore[2]  = {0, 0};

      for (UBaseType_t i = 0; i < taskCount; i++) {
        if (taskArray[i].xCoreID < 2) {
          totalCore[taskArray[i].xCoreID] += taskArray[i].ulRunTimeCounter;
          const char* name = taskArray[i].pcTaskName;
          if (name && (strncmp(name, "IDLE0", 5) == 0 || strncmp(name, "IDLE1", 5) == 0)) {
            idleCore[taskArray[i].xCoreID] += taskArray[i].ulRunTimeCounter;
          }
        }
      }

      // --- Filas por tarea (cpu_task) ---
      for (UBaseType_t i = 0; i < taskCount; i++) {
        uint32_t core = taskArray[i].xCoreID;
        if (core < 2 && totalCore[core] > 0) {
          float pct = 100.0f * (float)taskArray[i].ulRunTimeCounter / (float)totalCore[core];
          Serial.printf("%lu,cpu_task,%u,", ts, core);
          csvPrintQuoted(taskArray[i].pcTaskName); Serial.print(',');
          Serial.printf("%.2f,,,", pct); // pct_of_core, luego 3 vacíos
          Serial.printf("%lu,%llu,%lu", (unsigned long)taskArray[i].ulRunTimeCounter,
                        (unsigned long long)totalCore[core], (unsigned long)totalRuntime);
          Serial.println(",,,,,,,,"); // columnas de memoria vacías (8)
        }
      }

      // --- Filas por core (cpu_core) ---
      for (int core = 0; core < 2; core++) {
        float coreTotalPct = 0.0f, idlePct = 0.0f, busyPct = 0.0f;
        if (totalRuntime > 0) {
          coreTotalPct = 100.0f * (float)totalCore[core] / (float)totalRuntime;
          idlePct      = 100.0f * (float)idleCore[core]  / (float)totalRuntime;
          busyPct      = coreTotalPct - idlePct;
        }
        Serial.printf("%lu,cpu_core,%d,,", ts, core); // task vacío
        Serial.print(','); // pct_of_core vacío
        Serial.printf("%.2f,%.2f,%.2f,,%llu,%lu",
                      coreTotalPct, busyPct, idlePct,
                      (unsigned long long)totalCore[core],
                      (unsigned long)totalRuntime);
        Serial.println(",,,,,,,,"); // columnas de memoria vacías (8)
      }

      vPortFree(taskArray);
    }
  }
#endif

  // --------- MEMORIA (siempre) ----------
  size_t heapTotal = ESP.getHeapSize();
  size_t heapLibre = ESP.getFreeHeap();
  size_t heapUsado = heapTotal - heapLibre;
  float  heapPct   = heapTotal ? (heapUsado * 100.0f) / (float)heapTotal : 0.0f;

  bool hasPsram = psramFound();
  size_t psramTotal = hasPsram ? ESP.getPsramSize()  : 0;
  size_t psramLibre = hasPsram ? ESP.getFreePsram()  : 0;
  size_t psramUsado = hasPsram ? (psramTotal - psramLibre) : 0;
  float  psramPct   = (hasPsram && psramTotal) ? (psramUsado * 100.0f) / (float)psramTotal : 0.0f;

  Serial.printf(
    "%lu,mem,,,,,,,,,,%u,%u,%u,%.2f,%u,%u,%u,%.2f\n",
    ts,
    (unsigned)heapTotal, (unsigned)heapLibre, (unsigned)heapUsado, heapPct,
    (unsigned)psramTotal, (unsigned)psramLibre, (unsigned)psramUsado, psramPct
  );
}

// Tarea que imprime el header una vez y luego una muestra cada 5 s
static void StatsTaskCSV(void *pv) {
  (void)pv;
  printMetricsCSVHeader();
  for (;;) {
    printMetricsCSV();
    vTaskDelay(pdMS_TO_TICKS(5000));
  }
}

// ====== ARRANQUE/CONTROL EN MODO CSV ======
// Usá esta función alternativa para iniciar el muestreo en CSV por Serial:
void startMetricsCsv(){
  if (gStatsTask) return; // ya creada
  xTaskCreatePinnedToCore(
    StatsTaskCSV, "StatsTaskCSV",
    6144, NULL, 1, &gStatsTask,
    1 // core 1 (típicamente WiFi usa 0)
  );
}

// ===== API =====
void startMetrics(){
  if (gStatsTask) return; // ya creada
  xTaskCreatePinnedToCore(
    StatsTask, "StatsTask",
    4096, NULL, 1, &gStatsTask,
    1 // core 1 para no interferir con WiFi (típicamente core 0)
  );
}

void pauseMetrics(){
  if (gStatsTask) vTaskSuspend(gStatsTask);
}

void resumeMetrics(){
  if (gStatsTask) vTaskResume(gStatsTask);
}

void stopMetrics(){
  if (!gStatsTask) return;
  vTaskDelete(gStatsTask);
  gStatsTask = nullptr;
}

void printPerCoreTaskStats() {
  if (gStatsTask) xTaskNotifyGive(gStatsTask);
}
