#pragma once
#include <Arduino.h>
#include "esp_heap_caps.h"

/*
 * MetricsESP32.h — v2 (robusto)
 * - CPU% absoluto usando Run Time Stats de FreeRTOS si están disponibles.
 * - Fallback: idle hooks por core con calibración de "idle máximo" hecha al inicio.
 * - Contadores de TX/RX manuales (llamar notifyTx/notifyRx en callbacks MQTT).
 * - CSV cada 5 s: ts_ms,cpu_pct,free_heap,min_free_heap,tx_bytes,rx_bytes,energy_index
 *
 * RECOMENDADO: Llamar MetricsESP32::begin(); como PRIMERA línea de setup(),
 * antes de WiFi.begin/MQTT/sensores, para calibrar bien el "idle máximo".
 */

extern "C" {
  #include "freertos/FreeRTOS.h"
  #include "freertos/task.h"
  #include "freertos/portable.h"
}

namespace MetricsESP32 {

  // ------- Config -------
  static const uint32_t PERIOD_MS = 5000;
  static const uint32_t CALIBRATION_MS = 1200; // medir idle "máximo" al inicio
  static const float    K_CPU = 1.0f;
  static const float    K_NET = 0.0005f;

  // ------- Estado común -------
  static TaskHandle_t tHandle = nullptr;
  static uint64_t lastWallMs = 0;
  static volatile uint32_t txBytes = 0, rxBytes = 0;

  inline void notifyTx(size_t n) { txBytes += (uint32_t)n; }
  inline void notifyRx(size_t n) { rxBytes += (uint32_t)n; }

  static inline uint32_t freeHeap()    { return ESP.getFreeHeap(); }
  static inline uint32_t minFreeHeap() { return ESP.getMinFreeHeap(); }

  static float energyIndex(float cpuPercent, uint32_t tx, uint32_t rx) {
    return K_CPU * cpuPercent + K_NET * (tx + rx);
  }

  // ====== Opción A: Run Time Stats (preferida si está habilitado) ======
  #if ( configGENERATE_RUN_TIME_STATS == 1 )
    // Arduino-ESP32 activa por defecto el contador basado en esp_timer.
    // Calculamos CPU% como 100 * (1 - ΔidleTime / ΔtotalTime).
    static uint32_t lastIdleTime = 0;
    static uint32_t lastTotalTime = 0;

    static uint32_t getIdleTimeTicks() {
      // Sumar tiempo de las tareas idle (prioridad 0)
      UBaseType_t num = uxTaskGetNumberOfTasks();
      TaskStatus_t* list = (TaskStatus_t*) malloc(num * sizeof(TaskStatus_t));
      if (!list) return 0;
      UBaseType_t got = uxTaskGetSystemState(list, num, nullptr);
      uint32_t idle = 0;
      for (UBaseType_t i = 0; i < got; ++i) {
        if (list[i].uxBasePriority == tskIDLE_PRIORITY) {
          idle += list[i].ulRunTimeCounter;
        }
      }
      free(list);
      return idle;
    }

    static uint32_t getTotalTimeTicks() {
      UBaseType_t num = uxTaskGetNumberOfTasks();
      TaskStatus_t* list = (TaskStatus_t*) malloc(num * sizeof(TaskStatus_t));
      if (!list) return 0;
      uint32_t total = 0;
      UBaseType_t got = uxTaskGetSystemState(list, num, nullptr);
      for (UBaseType_t i = 0; i < got; ++i) total += list[i].ulRunTimeCounter;
      free(list);
      return total;
    }

    static void sampler(void*) {
      Serial.println(F("ts_ms,cpu_pct,free_heap,min_free_heap,tx_bytes,rx_bytes,energy_index"));

      lastIdleTime  = getIdleTimeTicks();
      lastTotalTime = getTotalTimeTicks();
      lastWallMs = millis();

      for (;;) {
        uint32_t txSnap = txBytes, rxSnap = rxBytes;
        txBytes = rxBytes = 0;
        vTaskDelay(pdMS_TO_TICKS(PERIOD_MS));

        uint32_t idleNow  = getIdleTimeTicks();
        uint32_t totalNow = getTotalTimeTicks();
        uint32_t idleDt   = idleNow  - lastIdleTime;
        uint32_t totalDt  = totalNow - lastTotalTime;

        double cpuPct = 0.0;
        if (totalDt > 0 && idleDt <= totalDt) {
          cpuPct = 100.0 * (1.0 - ((double)idleDt / (double)totalDt));
        }

        float eIdx = energyIndex((float)cpuPct, txSnap, rxSnap);
        unsigned long long ts = (unsigned long long)millis();
        Serial.printf("%llu,%.1f,%u,%u,%u,%u,%.2f\n",
          ts, cpuPct, freeHeap(), minFreeHeap(), txSnap, rxSnap, eIdx);

        lastIdleTime  = idleNow;
        lastTotalTime = totalNow;
      }
    }

  // ====== Opción B: Idle hook calibrado (si RT stats no está) ======
  #else
    #include "esp_freertos_hooks.h"
    static volatile uint32_t idleCountCore0 = 0, idleCountCore1 = 0;
    static double maxIdlePerMs = 0.0; // "idle máximo" calibrado

    static bool IRAM_ATTR idleHook0() { idleCountCore0++; return true; }
    static bool IRAM_ATTR idleHook1() { idleCountCore1++; return true; }

    static void calibrateIdleMax(uint32_t ms) {
      uint32_t i0 = idleCountCore0 + idleCountCore1;
      uint32_t t0 = millis();
      vTaskDelay(pdMS_TO_TICKS(ms));
      uint32_t i1 = idleCountCore0 + idleCountCore1;
      uint32_t t1 = millis();
      double dt = (double)(t1 - t0);
      maxIdlePerMs = dt > 0 ? (double)(i1 - i0) / dt : 0.0;
      if (maxIdlePerMs <= 0.0) maxIdlePerMs = 1.0;
    }

    static void sampler(void*) {
      esp_register_freertos_idle_hook_for_cpu(idleHook0, 0);
      esp_register_freertos_idle_hook_for_cpu(idleHook1, 1);

      calibrateIdleMax(CALIBRATION_MS);
      Serial.println(F("ts_ms,cpu_pct,free_heap,min_free_heap,tx_bytes,rx_bytes,energy_index"));

      uint32_t lastIdle = idleCountCore0 + idleCountCore1;
      uint32_t lastMs32 = millis();

      for (;;) {
        uint32_t txSnap = txBytes, rxSnap = rxBytes;
        txBytes = rxBytes = 0;
        vTaskDelay(pdMS_TO_TICKS(PERIOD_MS));

        uint32_t nowMs32 = millis();
        uint32_t idleNow = idleCountCore0 + idleCountCore1;
        double dt = (double)(nowMs32 - lastMs32);
        double idleDt = (double)(idleNow - lastIdle);

        double idleFrac = (maxIdlePerMs > 0.0) ? (idleDt / (dt * maxIdlePerMs)) : 0.0;
        if (idleFrac < 0.0) idleFrac = 0.0;
        if (idleFrac > 1.0) idleFrac = 1.0;
        double cpuPct = 100.0 * (1.0 - idleFrac);

        float eIdx = energyIndex((float)cpuPct, txSnap, rxSnap);
        unsigned long long ts = (unsigned long long)millis();
        Serial.printf("%llu,%.1f,%u,%u,%u,%u,%.2f\n",
          ts, cpuPct, freeHeap(), minFreeHeap(), txSnap, rxSnap, eIdx);

        lastMs32 = nowMs32;
        lastIdle = idleNow;
      }
    }
  #endif

  inline void begin() {
    xTaskCreatePinnedToCore(sampler, "metrics", 4096, nullptr, 1, &tHandle, ARDUINO_RUNNING_CORE);
  }

  inline void end() {
    if (tHandle) {
      vTaskDelete(tHandle);
      tHandle = nullptr;
    }
  }
}

/* ---------- Snippets de integración MQTT (ejemplos) ----------

#1) PubSubClient
----------------
#include <PubSubClient.h>
WiFiClient wifi;
PubSubClient mqtt(wifi);

void callback(char* topic, byte* payload, unsigned int length) {
  MetricsESP32::notifyRx(length);
  // ... tu código ...
}
void publicar(const char* topic, const char* msg) {
  if (mqtt.publish(topic, msg)) {
    MetricsESP32::notifyTx(strlen(msg));
  }
}

#2) AsyncMqttClient
-------------------
#include <AsyncMqttClient.h>
AsyncMqttClient mqtt;

void onMqttMessage(char* topic, char* payload, AsyncMqttClientMessageProperties properties,
                   size_t len, size_t index, size_t total) {
  MetricsESP32::notifyRx((uint32_t)len);
}
void publicar(const char* topic, const char* msg) {
  mqtt.publish(topic, 1, false, msg); // QoS1 ejemplo
  MetricsESP32::notifyTx(strlen(msg));
}

*/