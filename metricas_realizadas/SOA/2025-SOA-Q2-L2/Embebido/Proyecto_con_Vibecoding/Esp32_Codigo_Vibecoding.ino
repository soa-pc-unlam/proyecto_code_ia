#include <Arduino.h>
#include <TFT_eSPI.h>
#include <SPI.h>
#include <math.h>
#include <WiFi.h>
#include <PubSubClient.h>
#include "driver/i2s.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "freertos/queue.h"
#include "Metrics.h"   // <- métricas

// ----------------------------------------------------------------------------
// HARDWARE PINS & CONFIG
// ----------------------------------------------------------------------------
#define ENC1_CLK 21
#define ENC1_DT  22
#define ENC1_SW  27

#define ENC2_CLK 32
#define ENC2_DT  35
#define ENC2_SW  5

#define I2S_BCLK 33
#define I2S_LRCK 25
#define I2S_DOUT 26

#define SAMPLE_RATE 44100
#define NUM_STEPS 16
#define NUM_TRACKS 4

// ----------------------------------------------------------------------------
// WIFI & MQTT CONFIG
// ----------------------------------------------------------------------------
const char* SSID_NAME = "TeleCentro-9a5b_0_1";
const char* SSID_PASS = "NWYGJZZZM4CJ";

const char* MQTT_BROKER = "broker.emqx.io";
const int   MQTT_PORT   = 1883;
const char* CLIENT_ID   = "esp32_famico_sequencer";

const char* TOPIC_STATUS   = "/simulator/status";   // publish
const char* TOPIC_STATE    = "/simulator/state";    // subscribe
const char* TOPIC_TEMPO    = "/simulator/tempo";    // subscribe
const char* TOPIC_EDIT     = "/simulator/edit";     // subscribe
const char* TOPIC_PLAY_ROW = "/simulator/playrow";  // subscribe
const char* TOPIC_GET_CELL = "/simulator/getcell";  // subscribe
const char* TOPIC_CELL_VAL = "/simulator/cellval";  // publish

// ----------------------------------------------------------------------------
// GLOBAL OBJECTS
// ----------------------------------------------------------------------------
TFT_eSPI tft = TFT_eSPI();
WiFiClient espClient;
PubSubClient mqttClient(espClient);

// ----------------------------------------------------------------------------
// DATA STRUCTURES
// ----------------------------------------------------------------------------
enum AppState { STATE_IDLE, STATE_EDIT, STATE_PLAY_ALL, STATE_PLAY_LINE };
volatile AppState currentState = STATE_IDLE;

// Event Types for Queue
enum EventType {
  EVT_ENC1_ROT, EVT_ENC1_BTN,
  EVT_ENC2_ROT, EVT_ENC2_BTN,
  EVT_MQTT_CMD,
  EVT_PUB_STATUS, // para publicar estado
  EVT_PUB_MATRIX  // para publicar matriz
};

struct SysEvent {
  EventType type;
  int param1;
  int param2;
  char strData[32]; 
};

// Queue Handles
QueueHandle_t queueEvents;
QueueHandle_t queueMqttPub; 

// Sequencer Data
int matrixVals[16][4];
int bpm = 120;
int currentStep = 0;
int samplesPerStep = 0;

// UI State
int selectedRow = 0;
int selectedCol = 0; // 0-3 tracks, 4 = BPM
int scrollIndex = 0; // Which row is at the top of the screen
bool gMatrixDirty = true;
bool gUiDirty = true;

// ----------------------------------------------------------------------------
// METRICAS
// ----------------------------------------------------------------------------
bool gMetricsIdleDone = false;
unsigned long gMetricsIdleStartMs = 0;
bool gMetricsPlayAllActive = false;

// ----------------------------------------------------------------------------
// AUDIO ENGINE VARS
// ----------------------------------------------------------------------------
struct Oscillator {
  float phase;
  float increment;
  float amplitude;
  float decay;
  int   type; // 0=Noise, 1=Square, 2=Tri
  bool  active;
};

Oscillator tracks[4];
unsigned long totalSamplesPlayed = 0;
long previewSamplesLeft = 0;

// ----------------------------------------------------------------------------
// SETUP AUDIO
// ----------------------------------------------------------------------------
void setupI2S() {
  i2s_config_t i2s_config = {
    .mode = (i2s_mode_t)(I2S_MODE_MASTER | I2S_MODE_TX),
    .sample_rate = SAMPLE_RATE,
    .bits_per_sample = I2S_BITS_PER_SAMPLE_16BIT,
    .channel_format = I2S_CHANNEL_FMT_RIGHT_LEFT,
    .communication_format = I2S_COMM_FORMAT_I2S_MSB,
    .intr_alloc_flags = ESP_INTR_FLAG_LEVEL1,
    .dma_buf_count = 8,
    .dma_buf_len = 64,
    .use_apll = false,
    .tx_desc_auto_clear = true
  };
  
  i2s_pin_config_t pin_config = {
    .bck_io_num = I2S_BCLK,
    .ws_io_num = I2S_LRCK,
    .data_out_num = I2S_DOUT,
    .data_in_num = I2S_PIN_NO_CHANGE
  };

  i2s_driver_install(I2S_NUM_0, &i2s_config, 0, NULL);
  i2s_set_pin(I2S_NUM_0, &pin_config);
  i2s_set_clk(I2S_NUM_0, SAMPLE_RATE, I2S_BITS_PER_SAMPLE_16BIT, I2S_CHANNEL_STEREO);
}

// ----------------------------------------------------------------------------
// NETWORK HELPER FUNCTIONS
// ----------------------------------------------------------------------------
void connectWiFi() {
  WiFi.begin(SSID_NAME, SSID_PASS);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
  }
}

void mqttCallback(char* topic, byte* payload, unsigned int length) {
  SysEvent evt;
  evt.type = EVT_MQTT_CMD;
  evt.param1 = 0; // Placeholder

  // Determine command type based on topic
  String t = String(topic);
  String p = "";
  for (unsigned int i = 0; i < length; i++) p += (char)payload[i];
  
  // Pack into event structure carefully
  if (t == TOPIC_STATE) {
    if (p.equalsIgnoreCase("IDLE")) evt.param1 = 1;
    else if (p.equalsIgnoreCase("EDIT")) evt.param1 = 2;
    else if (p.equalsIgnoreCase("PLAY_ALL")) evt.param1 = 3;
    else if (p.equalsIgnoreCase("PLAY_LINE")) evt.param1 = 4;
    else return;
    xQueueSend(queueEvents, &evt, 0);
  }
  else if (t == TOPIC_TEMPO) {
    int val = p.toInt();
    if (val > 0) {
      evt.param1 = 10; // Code for TEMPO
      evt.param2 = val;
      xQueueSend(queueEvents, &evt, 0);
    }
  }
  else if (t == TOPIC_PLAY_ROW) {
    int row = p.toInt();
    if (row >= 0 && row < 16) {
      evt.param1 = 11; // Code for PLAY_ROW
      evt.param2 = row;
      xQueueSend(queueEvents, &evt, 0);
    }
  }
  else if (t == TOPIC_EDIT) {
    // "r c v"
    evt.param1 = 12;
    strncpy(evt.strData, p.c_str(), 31);
    xQueueSend(queueEvents, &evt, 0);
  }
  else if (t == TOPIC_GET_CELL) {
    if (p == "SEND_MATRIX") {
       evt.param1 = 13; // Request dump
       xQueueSend(queueEvents, &evt, 0);
    } else {
       evt.param1 = 14; 
       xQueueSend(queueEvents, &evt, 0);
    }
  }
}

void connectMQTT() {
  mqttClient.setServer(MQTT_BROKER, MQTT_PORT);
  mqttClient.setCallback(mqttCallback);

  while (!mqttClient.connected()) {
    if (mqttClient.connect(CLIENT_ID)) {
      mqttClient.subscribe(TOPIC_STATE);
      mqttClient.subscribe(TOPIC_TEMPO);
      mqttClient.subscribe(TOPIC_EDIT);
      mqttClient.subscribe(TOPIC_PLAY_ROW);
      mqttClient.subscribe(TOPIC_GET_CELL);
      mqttClient.publish(TOPIC_STATUS, "IDLE");
    } else {
      vTaskDelay(2000 / portTICK_PERIOD_MS);
    }
  }
}

// ----------------------------------------------------------------------------
// TASKS
// ----------------------------------------------------------------------------

// Task: Read Encoders
void vInputTask(void *parameter) {
  int lastClk1 = digitalRead(ENC1_CLK);
  int lastClk2 = digitalRead(ENC2_CLK);
  int lastSw1 = digitalRead(ENC1_SW);
  int lastSw2 = digitalRead(ENC2_SW);
  
  unsigned long lastDebounce1 = 0;
  unsigned long lastDebounce2 = 0;

  for(;;) {
    int clk1 = digitalRead(ENC1_CLK);
    int sw1 = digitalRead(ENC1_SW);
    int clk2 = digitalRead(ENC2_CLK);
    int sw2 = digitalRead(ENC2_SW);

    // ENC 1 Rotation
    if (clk1 != lastClk1 && clk1 == LOW) {
      SysEvent e; 
      e.type = EVT_ENC1_ROT;
      e.param1 = (digitalRead(ENC1_DT) != clk1) ? 1 : -1;
      xQueueSend(queueEvents, &e, 0);
    }
    lastClk1 = clk1;

    // ENC 2 Rotation
    if (clk2 != lastClk2 && clk2 == LOW) {
      SysEvent e;
      e.type = EVT_ENC2_ROT;
      e.param1 = (digitalRead(ENC2_DT) != clk2) ? 1 : -1;
      xQueueSend(queueEvents, &e, 0);
    }
    lastClk2 = clk2;

    // ENC 1 Button
    if (sw1 == LOW && lastSw1 == HIGH && (millis() - lastDebounce1 > 200)) {
      SysEvent e; e.type = EVT_ENC1_BTN;
      xQueueSend(queueEvents, &e, 0);
      lastDebounce1 = millis();
    }
    lastSw1 = sw1;

    // ENC 2 Button
    if (sw2 == LOW && lastSw2 == HIGH && (millis() - lastDebounce2 > 200)) {
      SysEvent e; e.type = EVT_ENC2_BTN;
      xQueueSend(queueEvents, &e, 0);
      lastDebounce2 = millis();
    }
    lastSw2 = sw2;

    vTaskDelay(2 / portTICK_PERIOD_MS);
  }
}

// Task: MQTT Loop & Publishing
void vMqttTask(void *parameter) {
  if (WiFi.status() != WL_CONNECTED) connectWiFi();
  connectMQTT();

  char msgBuffer[1024]; // Large buffer for matrix dump

  for(;;) {
    if (!mqttClient.connected()) connectMQTT();
    mqttClient.loop();

    // Check if we need to publish something
    SysEvent pubReq;
    if (xQueueReceive(queueMqttPub, &pubReq, 0) == pdTRUE) {
      if (pubReq.type == EVT_PUB_STATUS) { // STATUS UPDATE
        mqttClient.publish(TOPIC_STATUS, pubReq.strData);
      } 
      else if (pubReq.type == EVT_PUB_MATRIX) { // MATRIX DUMP
        // Build the string
        String s = "";
        for(int r=0; r<16; r++){
          for(int c=0; c<4; c++){
            s += String(matrixVals[r][c]);
            if(r!=15 || c!=3) s += " ";
          }
        }
        s.toCharArray(msgBuffer, 1024);
        mqttClient.publish(TOPIC_CELL_VAL, msgBuffer);
      }
    }

    vTaskDelay(10 / portTICK_PERIOD_MS);
  }
}

// ----------------------------------------------------------------------------
// AUDIO LOGIC
// ----------------------------------------------------------------------------
float midiToHz(int note) {
  if (note == 0) return 0;
  return 440.0f * pow(2.0f, (note - 69.0f) / 12.0f);
}

void triggerStep(int step) {
  // Track 0: Noise
  if (matrixVals[step][0] > 0) {
    tracks[0].active = true;
    tracks[0].amplitude = 1.0;
    tracks[0].decay = 0.90; // Fast decay for drums
    tracks[0].type = 0;
  }
  
  // Track 1-3: Tone
  for (int i = 1; i < 4; i++) {
    int note = matrixVals[step][i];
    if (note > 0) {
      tracks[i].active = true;
      tracks[i].amplitude = 0.8;
      tracks[i].decay = 0.99; // Slower decay for tone
      tracks[i].increment = midiToHz(note) * 65536.0f / SAMPLE_RATE;
      tracks[i].type = (i == 3) ? 2 : 1; // 3 is Triangle, others Square
    }
  }
}

int16_t generateSample() {
  int32_t mix = 0;

  for (int i = 0; i < 4; i++) {
    if (!tracks[i].active) continue;

    int16_t sample = 0;
    // Update Phase
    tracks[i].phase += tracks[i].increment;
    if (tracks[i].phase >= 65536.0f) tracks[i].phase -= 65536.0f;

    // Generate Wave
    if (tracks[i].type == 0) { // Noise
      sample = (rand() % 20000) - 10000;
    } else if (tracks[i].type == 1) { // Square
      sample = (tracks[i].phase > 32768.0f) ? 10000 : -10000;
    } else if (tracks[i].type == 2) { // Triangle
      float p = tracks[i].phase / 65536.0f;
      sample = (p < 0.5f) ? (p * 4.0f - 1.0f) * 10000 : ((1.0f - p) * 4.0f - 1.0f) * 10000;
    }

    // Apply Envelope
    sample = (int16_t)(sample * tracks[i].amplitude);
    tracks[i].amplitude *= tracks[i].decay;

    if (tracks[i].amplitude < 0.01) tracks[i].active = false;
    
    mix += sample;
  }

  // Clip
  if (mix > 32000) mix = 32000;
  if (mix < -32000) mix = -32000;

  return (int16_t)mix;
}

// ----------------------------------------------------------------------------
// INITIALIZATION
// ----------------------------------------------------------------------------
void setup() {
  Serial.begin(115200);

  // Métricas: ventana de reposo
  initStats();
  gMetricsIdleDone      = false;
  gMetricsPlayAllActive = false;
  gMetricsIdleStartMs   = millis();

  // Default Matrix Pattern
  for(int i=0; i<16; i+=4) matrixVals[i][0] = 1; // Kick every 4
  for(int i=0; i<16; i++) matrixVals[i][1] = 0;
  matrixVals[0][1] = 60; matrixVals[4][1] = 64; matrixVals[8][1] = 67; matrixVals[12][1] = 72; 

  // Pins
  pinMode(ENC1_CLK, INPUT_PULLUP); pinMode(ENC1_DT, INPUT_PULLUP); pinMode(ENC1_SW, INPUT_PULLUP);
  pinMode(ENC2_CLK, INPUT_PULLUP); pinMode(ENC2_DT, INPUT_PULLUP); pinMode(ENC2_SW, INPUT_PULLUP);

  // TFT
  tft.init();
  tft.setRotation(1);
  tft.fillScreen(TFT_BLACK);
  tft.setTextColor(TFT_WHITE, TFT_BLACK);

  // I2S
  setupI2S();

  // FreeRTOS
  queueEvents = xQueueCreate(20, sizeof(SysEvent));
  queueMqttPub = xQueueCreate(5, sizeof(SysEvent));

  xTaskCreate(vInputTask, "InputTask", 2048, NULL, 1, NULL);
  xTaskCreate(vMqttTask, "MqttTask", 4096, NULL, 1, NULL);

  // Recalc Audio
  samplesPerStep = (SAMPLE_RATE * 60) / (bpm * 4);
}

// ----------------------------------------------------------------------------
// MAIN LOOP (UI + AUDIO + LOGIC)
// ----------------------------------------------------------------------------
void loop() {
  // 0. Métricas: ventana de reposo de 10 segundos
  if (!gMetricsIdleDone) {
    if (millis() - gMetricsIdleStartMs >= 10000UL) {
      Serial.println("========== METRICAS: SISTEMA EN REPOSO (10s) ==========");
      finishStats();
      gMetricsIdleDone = true;
    }
  }

  // 1. Process Events
  SysEvent evt;
  if (xQueueReceive(queueEvents, &evt, 0) == pdTRUE) {
    
    // MQTT COMMANDS
    if (evt.type == EVT_MQTT_CMD) {
      if (evt.param1 == 1) {          // IDLE
        // si venimos de PLAY_ALL por MQTT u otra causa, cerramos medición
        if (currentState == STATE_PLAY_ALL && gMetricsPlayAllActive) {
          Serial.println("========== METRICAS: PLAY_ALL desde MQTT (fin) ==========");
          finishStats();
          gMetricsPlayAllActive = false;
        }
        currentState = STATE_IDLE;
      }
      else if (evt.param1 == 2) {     // EDIT
        currentState = STATE_EDIT;
      }
      else if (evt.param1 == 3) {     // PLAY_ALL desde MQTT
        // Arrancamos nueva ventana de métricas para esta acción
        if (!gMetricsPlayAllActive) {
          Serial.println("========== METRICAS: PLAY_ALL desde MQTT (inicio) ==========");
          initStats();
          gMetricsPlayAllActive = true;
        }
        currentState = STATE_PLAY_ALL;
        currentStep = 0;
      }
      else if (evt.param1 == 4) {     // PLAY_LINE
        currentState = STATE_PLAY_LINE; 
      }
      else if (evt.param1 == 10) {    // TEMPO
        bpm = evt.param2; 
        samplesPerStep = (SAMPLE_RATE * 60) / (bpm * 4);
      }
      else if (evt.param1 == 11) {    // PLAY_ROW
        currentState = STATE_PLAY_LINE; 
        currentStep = evt.param2; 
        previewSamplesLeft = samplesPerStep; 
        triggerStep(currentStep);
      }
      else if (evt.param1 == 12) {    // EDIT r c v
        int r, c, v;
        if (sscanf(evt.strData, "%d %d %d", &r, &c, &v) == 3) {
          if (r>=0 && r<16 && c>=0 && c<4) matrixVals[r][c] = v;
        }
        gMatrixDirty = true;
      }
      else if (evt.param1 == 13) {    // SEND_MATRIX
        SysEvent pub; 
        pub.type = EVT_PUB_MATRIX;
        xQueueSend(queueMqttPub, &pub, 0);
      }
      gUiDirty = true;
    }
    
    // ENCODER COMMANDS
    if (evt.type == EVT_ENC1_ROT) { // Enc 1: cambia valor
       if (selectedCol == 4) {
         bpm = constrain(bpm + evt.param1, 20, 300);
         samplesPerStep = (SAMPLE_RATE * 60) / (bpm * 4);
       } else {
         int limit = (selectedCol == 0) ? 15 : 127;
         matrixVals[selectedRow][selectedCol] = constrain(matrixVals[selectedRow][selectedCol] + evt.param1, 0, limit);
         gMatrixDirty = true;
       }
       gUiDirty = true;
    }
    else if (evt.type == EVT_ENC2_ROT) { // Enc 2: cambia columna
       selectedCol += evt.param1;
       if (selectedCol < 0) selectedCol = 0;
       if (selectedCol > 4) selectedCol = 4;
       gUiDirty = true;
    }
    else if (evt.type == EVT_ENC1_BTN) { // Button 1: cambia fila
       selectedRow++;
       if (selectedRow >= 16) selectedRow = 0;
       if (selectedRow > scrollIndex + 5) scrollIndex = selectedRow - 5;
       if (selectedRow < scrollIndex)     scrollIndex = selectedRow;
       gUiDirty = true;
    }
    else if (evt.type == EVT_ENC2_BTN) { // Button 2: Toggle Play/Stop local
       if (currentState == STATE_PLAY_ALL) {
         // si se estaba midiendo un PLAY_ALL por MQTT, también podemos cerrarlo acá
         if (gMetricsPlayAllActive) {
           Serial.println("========== METRICAS: PLAY_ALL desde MQTT (fin) ==========");
           finishStats();
           gMetricsPlayAllActive = false;
         }
         currentState = STATE_IDLE;
         SysEvent s; 
         s.type = EVT_PUB_STATUS; 
         strcpy(s.strData, "IDLE"); 
         xQueueSend(queueMqttPub, &s, 0);
       } else {
         currentState = STATE_PLAY_ALL;
         currentStep = 0;
         SysEvent s; 
         s.type = EVT_PUB_STATUS; 
         strcpy(s.strData, "PLAY_ALL"); 
         xQueueSend(queueMqttPub, &s, 0);
         // NOTA: acá NO iniciamos métricas, porque el requerimiento es solo para PLAY_ALL vía MQTT
       }
       gUiDirty = true;
    }
  }

  // 2. Audio Engine Logic
  int samplesToWrite = 256; // Process in chunks
  int16_t audioBuf[512]; // stereo * 256

  if (currentState == STATE_PLAY_ALL) {
    for (int k=0; k<samplesToWrite; k++) {
      totalSamplesPlayed++;
      if (totalSamplesPlayed >= (unsigned long)samplesPerStep) {
        totalSamplesPlayed = 0;
        currentStep = (currentStep + 1) % 16;
        triggerStep(currentStep);
        gUiDirty = true; // Update cursor highlight
      }
      int16_t s = generateSample();
      audioBuf[k*2] = s;
      audioBuf[k*2+1] = s;
    }
  } else if (currentState == STATE_PLAY_LINE) {
    for (int k=0; k<samplesToWrite; k++) {
      if (previewSamplesLeft > 0) {
        previewSamplesLeft--;
        int16_t s = generateSample();
        audioBuf[k*2] = s;
        audioBuf[k*2+1] = s;
      } else {
        audioBuf[k*2] = 0; 
        audioBuf[k*2+1] = 0;
        // End of line
        currentState = STATE_EDIT; // Return to edit
        gUiDirty = true;
      }
    }
  } else {
    // Silence
    memset(audioBuf, 0, sizeof(audioBuf));
  }

  // Write to I2S
  size_t bytesWritten;
  i2s_write(I2S_NUM_0, audioBuf, sizeof(audioBuf), &bytesWritten, portMAX_DELAY);

  // 3. UI Rendering
  if (gUiDirty || gMatrixDirty) {
    
    // Draw Status Header
    tft.fillRect(0, 0, 320, 20, TFT_DARKGREY);
    tft.setCursor(5, 5);
    tft.setTextSize(1);
    
    String stateStr = "IDLE";
    if (currentState == STATE_EDIT) stateStr = "EDIT";
    else if (currentState == STATE_PLAY_ALL) stateStr = "PLAY";
    else if (currentState == STATE_PLAY_LINE) stateStr = "LINE";
    
    tft.printf("%s | BPM: %d | R:%d C:%d", stateStr.c_str(), bpm, selectedRow, selectedCol);

    // Draw Headers / Icons
    int colWidth = 60;
    int startY = 30;
    int gridH = 25;
    
    // Icons: 0=Circle(Drums), 1=Sqr, 2=Sqr, 3=Tri
    tft.fillCircle(30, 25, 4, TFT_RED);
    tft.fillRect(80, 21, 8, 8, TFT_GREEN);
    tft.fillRect(140, 21, 8, 8, TFT_GREEN);
    tft.fillTriangle(200, 28, 204, 21, 208, 28, TFT_YELLOW);
    tft.setCursor(260, 22); tft.print("STEP");

    // Draw Grid (6 rows window)
    for (int i = 0; i < 6; i++) {
      int rowIdx = scrollIndex + i;
      if (rowIdx >= 16) break;

      int y = startY + (i * gridH);
      
      bool isPlayingRow  = (currentState == STATE_PLAY_ALL && rowIdx == currentStep);
      bool isSelectedRow = (rowIdx == selectedRow);

      for (int c = 0; c < 5; c++) { // 0-3 vals, 4 step num
        int x = c * colWidth;
        uint16_t bgColor = TFT_BLACK;
        uint16_t txtColor = TFT_WHITE;

        if (c < 4) { // Data cells
           if (isSelectedRow && selectedCol == c) {
              bgColor = TFT_WHITE;
              txtColor = TFT_BLACK;
           } else if (isPlayingRow) {
              bgColor = TFT_DARKGREEN;
           }
           
           tft.fillRect(x, y, colWidth-2, gridH-2, bgColor);
           tft.setTextColor(txtColor);
           tft.setCursor(x+10, y+5);
           tft.print(matrixVals[rowIdx][c]);
        } else { // Step number col
           tft.setTextColor(TFT_LIGHTGREY);
           tft.setCursor(x+10, y+5);
           tft.print(rowIdx);
        }
      }
    }
    
    // Draw BPM selection indicator
    if (selectedCol == 4) {
      tft.drawRect(50, 0, 70, 20, TFT_RED);
    }

    gUiDirty = false;
    gMatrixDirty = false;
  }
}
