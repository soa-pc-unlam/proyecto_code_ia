#include <Arduino.h>
#include <TFT_eSPI.h>
#include <SPI.h>
#include <math.h>
#include "driver/i2s.h"
#include "Metrics.h"

// FreeRTOS
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "freertos/queue.h"

// ================================
//           WIFI + MQTT
// ================================
#include <WiFi.h>
#include <PubSubClient.h>

const char* WIFI_SSID = "TeleCentro-9a5b_0_1";
const char* WIFI_PASS = "NWYGJZZZM4CJ";
const char* MQTT_BROKER    = "broker.emqx.io";
const int   MQTT_PORT      = 1883;
const char* MQTT_CLIENT_ID = "esp32_famico_sequencer";

WiFiClient wifiClient;
PubSubClient mqttClient(wifiClient);

// Topics
const char* TOPIC_STATUS    = "/simulator/status";   // publish
const char* TOPIC_STATE     = "/simulator/state";    // subscribe
const char* TOPIC_TEMPO     = "/simulator/tempo";    // subscribe (bpm o ms/step)
const char* TOPIC_EDIT      = "/simulator/edit";     // subscribe "r c v"
const char* TOPIC_PLAY_ROW  = "/simulator/playrow";  // subscribe "r"
const char* TOPIC_GET_CELL  = "/simulator/getcell";  // subscribe: pedidos / matriz desde Android
const char* TOPIC_CELL_VAL  = "/simulator/cellval";  // publish : matriz completa hacia Android

// ================================
//           TFT (ILI9341)
// ================================
TFT_eSPI tft;

// ------- Matriz 4x16 - patrón de notas -------
constexpr int ROWS = 16;
constexpr int COLS = 4;
int matrixVals[ROWS][COLS];
int scrollIndex = 0;
constexpr int VISIBLE_ROWS = 6;

// Layout de matriz
constexpr int MATRIX_X0   = 12;
constexpr int MATRIX_Y0   = 64;
constexpr int CELL_W      = 65;
constexpr int CELL_H      = 24;
constexpr int CELL_GAP_X  = 0;
constexpr int CELL_GAP_Y  = 0;

// Columna extra: número de step a la derecha
constexpr int STEP_COL_W    = 28;
constexpr int STEP_COL_PADY = 3;

// Header íconos
constexpr int HEADER_ICON_Y = MATRIX_Y0 - 26;
constexpr int HEADER_ICON_R = 9;

// ================================
//           ENCODERS
// ================================
#define ENC1_CLK 21
#define ENC1_DT  22
#define ENC1_SW  27

#define ENC2_CLK 32
#define ENC2_DT  35
#define ENC2_SW   5

volatile int selectedRow = 0;
volatile int selectedCol = 0;

// Tiempos de debounce / guard
const unsigned long ENC_DEBOUNCE_MS = 6;
const unsigned long ENC_GUARD_MS    = 18;
const unsigned long BTN_DEBOUNCE_MS = 220;

struct EncState {
  int  lastDir = 0;
  unsigned long lastTickMs = 0;
  unsigned long lastEdgeMs = 0;
  int prevCLK = HIGH;
  unsigned long lastBtnMs = 0;
};

EncState enc1, enc2;

// ================================
//           EVENTOS
// ================================
enum Event : uint8_t {
  EV_NONE = 0,
  EV_ENC1_CW,
  EV_ENC1_CCW,
  EV_ENC1_PRESS,
  EV_ENC2_CW,
  EV_ENC2_CCW,
  EV_ENC2_PRESS,
  EV_MQTT_TO_IDLE,
  EV_MQTT_TO_EDIT,
  EV_MQTT_TO_PLAY_ALL,
  EV_MQTT_TO_PLAY_LINE,
  EV_MQTT_PLAY_ROW,
  EV_MQTT_TEMPO_SET
};

QueueHandle_t queueEvents = nullptr;
TaskHandle_t  hInputTask  = nullptr;
TaskHandle_t  hMqttTask   = nullptr;

// ================================
//       COLA PARA MQTT (publish)
// ================================
enum MqttCmdType : uint8_t { MQTT_CMD_STATUS = 0, MQTT_CMD_MATRIX = 1 };

struct MqttCommand {
  MqttCmdType type;
  char payload[256];
};

QueueHandle_t queueMqtt = nullptr;

// Parámetros FreeRTOS
constexpr int EVENT_QUEUE_LEN             = 16;
constexpr uint16_t INPUT_TASK_STACK       = 4096;
constexpr UBaseType_t INPUT_TASK_PRIO     = 2;
constexpr uint16_t MQTT_TASK_STACK        = 4096;
constexpr UBaseType_t MQTT_TASK_PRIO      = 1;
constexpr TickType_t INPUT_TASK_PERIOD_MS = 2;
constexpr TickType_t MQTT_TASK_PERIOD_MS  = 10;

// ================================
//            AUDIO (I2S)
// ================================
static const int I2S_SR   = 44100;
static const i2s_port_t I2S_PORT = I2S_NUM_0;
static const int PIN_BCLK = 33; // BCLK
static const int PIN_LRCK = 25; // LRCLK / WS
static const int PIN_DOUT = 26; // DIN -> MAX98357A

// preview de línea (duración)
int previewSamplesLeft = 0;

static const int STEPS = ROWS;
static int step = 0;
static int samplesPerStep = 0;

// --- BPM editable ---
static int BPM = 120;
static const int BPM_MIN  = 20;
static const int BPM_MAX  = 300;
static const int BPM_STEP = 10;

// Parámetros audio
constexpr float MASTER_DRUMS   = 0.75f;
constexpr float GAIN_NOISE     = 0.80f;
constexpr float DECAY_NOISE    = 0.9950f;
constexpr float MASTER_SYNTH   = 0.30f;
constexpr size_t AUDIO_FRAMES  = 512;

// NES NOISE
static const uint16_t NES_NOISE_PERIOD[16] = {
  4, 8, 16, 32, 64, 96, 128, 160, 202, 254, 380, 508, 762, 1016, 2034, 4068
};
static const double NES_APU_CLOCK = 1789773.0; // Hz

struct NesNoise {
  uint16_t lfsr = 0x7FFF;
  int stepSamples = 1;
  int stepCounter = 0;
  bool shortMode = false;

  static int periodToStepSamples(uint16_t period) {
    const double lfsrHz = NES_APU_CLOCK / (double)period;
    int s = (int)round(I2S_SR / lfsrHz);
    return s < 1 ? 1 : s;
  }
  void setNote(int idx) {
    if (idx < 0) idx = 0;
    if (idx > 15) idx = 15;
    stepSamples = periodToStepSamples(NES_NOISE_PERIOD[idx]);
  }
  void setShort(bool s) { shortMode = s; }

  float tick() {
    if (++stepCounter >= stepSamples) {
      stepCounter = 0;
      uint16_t bit = shortMode ? ((lfsr ^ (lfsr >> 6)) & 1U)
                               : ((lfsr ^ (lfsr >> 1)) & 1U);
      lfsr = (uint16_t)((lfsr >> 1) | (bit << 14));
    }
    return (lfsr & 1U) ? 1.0f : -1.0f;
  }
};

static NesNoise nNoise;
static float    envNoise = 0.0f;

struct SqOsc {
  double phase=0, inc=0;
  float amp=0.12f;
  void setFreq(float f){ inc = (f<=0)?0.0:(double)f/I2S_SR; }
  float tick(){
    if(inc==0) return 0;
    float frac=float(phase-(uint32_t)phase);
    float s = (frac<0.5f)?+1.0f:-1.0f;
    phase+=inc;
    if(phase>=1.0) phase-=1.0;
    return s*amp;
  }
};

struct TriOsc{
  double phase=0, inc=0;
  float amp=0.5f;
  void setFreq(float f){ inc = (f<=0)?0.0:(double)f/I2S_SR; }
  float tick(){
    if(inc==0) return 0;
    double p=phase;
    float y=(p<0.5)?(float)(-1+4*p):(float)(3-4*p);
    phase+=inc;
    if(phase>=1.0) phase-=1.0;
    return y*amp;
  }
};

static SqOsc  sq1, sq2;
static TriOsc tri;

// ================================
//           UI FLAGS
// ================================
bool gUiEditMode    = false;
bool gHighlightStep = false;

// Redibujar matriz cuando la toca MQTT
volatile bool gMatrixDirty = false;

// Aux para eventos MQTT
volatile int  gMqttPlayRow   = 0;
volatile int  gMqttTempoBpm  = 120;

// ================================
//           METRICAS
// ================================
bool gMetricsIdleDone = false;
unsigned long gMetricsIdleStartMs = 0;
bool gMetricsPlayAllActive = false;

// ================================
//           DIBUJO TFT
// ================================
constexpr int STATUS_W = 92;
constexpr int STATUS_H = 27;
constexpr int STATUS_X_PAD = 4;
inline int statusBoxX() { return tft.width() - STATUS_W - STATUS_X_PAD; }
inline int statusBoxY() { return 10; }

void drawColumnHeaders(); // forward
void drawStatus(const char* modeTxt); // forward
void redrawVisibleWindow();           // forward

void drawHeader() {
  tft.fillScreen(TFT_BLACK);
  tft.drawRect(0, 0, tft.width(), tft.height(), TFT_DARKGREY);

  tft.setTextFont(4);
  tft.setTextColor(TFT_WHITE, TFT_BLACK);
  tft.drawCentreString("FAMICO", tft.width()/2, 4, 4);

  tft.drawRoundRect(6, 52, tft.width()-12, tft.height()-60, 8, TFT_SKYBLUE);

  drawColumnHeaders();
}

void drawColIcon(int c, int cx, int cy) {
  switch (c) {
    case 0:
      tft.fillCircle(cx, cy, HEADER_ICON_R, TFT_YELLOW);
      tft.drawCircle(cx, cy, HEADER_ICON_R, TFT_DARKGREY);
      tft.fillCircle(cx-3, cy-2, 1, TFT_BLACK);
      tft.fillCircle(cx+3, cy-2, 1, TFT_BLACK);
      tft.drawLine(cx-5, cy+2, cx+5, cy+2, TFT_BLACK);
      break;
    case 1: {
      int s = HEADER_ICON_R*2-2;
      tft.fillRect(cx-s/2, cy-s/2, s, s, TFT_CYAN);
      tft.drawRect(cx-s/2, cy-s/2, s, s, TFT_DARKGREY);
      break;
    }
    case 2: {
      int s = HEADER_ICON_R*2-2;
      tft.fillRect(cx-s/2, cy-s/2, s, s, TFT_GREEN);
      tft.drawRect(cx-s/2, cy-s/2, s, s, TFT_DARKGREEN);
      tft.fillRect(cx-3, cy-2, 2, 2, TFT_BLACK);
      tft.fillRect(cx+1, cy-2, 2, 2, TFT_BLACK);
      break;
    }
    case 3: {
      int r=HEADER_ICON_R+1;
      tft.fillTriangle(cx, cy-r, cx-r, cy+r-2, cx+r, cy+r-2, TFT_ORANGE);
      tft.drawTriangle(cx, cy-r, cx-r, cy+r-2, cx+r, cy+r-2, TFT_DARKGREY);
      break;
    }
  }
}

void drawColumnHeaders() {
  for (int c = 0; c < COLS; ++c) {
    int cx = MATRIX_X0 + c * (CELL_W + CELL_GAP_X) + (CELL_W/2) - 1;
    drawColIcon(c, cx, HEADER_ICON_Y);
  }
}

void drawScrollBar() {
  const int railX = tft.width() - 10;
  const int railY = MATRIX_Y0;
  const int gridH = VISIBLE_ROWS * (CELL_H + CELL_GAP_Y);
  const int railH = gridH - 2;

  tft.fillRect(railX, railY, 4, railH, TFT_DARKGREY);
  int scrollBarH = max(6, (VISIBLE_ROWS * railH) / ROWS);
  const int maxScroll = max(1, ROWS - VISIBLE_ROWS);
  int scrollY = railY + (scrollIndex * (railH - scrollBarH)) / maxScroll;
  tft.fillRect(railX, scrollY, 4, scrollBarH, TFT_GREEN);
}

static inline void cellXY(int rowIndex, int colIndex, int& x, int& y) {
  int r = rowIndex - scrollIndex;
  x = MATRIX_X0 + colIndex * (CELL_W + CELL_GAP_X);
  y = MATRIX_Y0 + r * (CELL_H + CELL_GAP_Y);
}

void drawMatrixRow(int rowIndex, bool rowHighlighted) {
  int r = rowIndex - scrollIndex;
  if (r < 0 || r >= VISIBLE_ROWS) return;

  int y = MATRIX_Y0 + r * (CELL_H + CELL_GAP_Y);
  uint16_t bg_row = rowHighlighted ? TFT_NAVY : TFT_BLACK;
  uint16_t fg_row = rowHighlighted ? TFT_YELLOW : TFT_WHITE;
  uint16_t frame  = rowHighlighted ? TFT_YELLOW : TFT_DARKGREY;

  for (int c = 0; c < COLS; c++) {
    int x = MATRIX_X0 + c * (CELL_W + CELL_GAP_X);
    tft.fillRect(x, y, CELL_W - 2, CELL_H - 2, bg_row);
    tft.drawRect(x, y, CELL_W - 2, CELL_H - 2, frame);
    tft.setTextColor(fg_row, bg_row);
    tft.drawCentreString(String(matrixVals[rowIndex][c]), x + (CELL_W/2) - 1, y + 3, 2);
  }

  int xStep = MATRIX_X0 + COLS * (CELL_W + CELL_GAP_X);
  uint16_t bgStep = rowHighlighted ? TFT_DARKGREEN : TFT_BLACK;
  uint16_t fgStep = rowHighlighted ? TFT_WHITE : TFT_SILVER;
  tft.fillRect(xStep, y, STEP_COL_W - 2, CELL_H - 2, bgStep);
  tft.drawRect(xStep, y, STEP_COL_W - 2, CELL_H - 2, TFT_DARKGREY);
  tft.setTextColor(fgStep, bgStep);
  tft.drawCentreString(String(rowIndex), xStep + (STEP_COL_W/2) - 1, y + STEP_COL_PADY, 2);
}

void drawBpmCursor(bool on) {
  uint16_t col = on ? TFT_ORANGE : TFT_BLACK;
  int x = statusBoxX(), y = statusBoxY();
  tft.drawRect(x-1, y-1, STATUS_W+2, STATUS_H+2, col);
  tft.drawRect(x-2, y-2, STATUS_W+4, STATUS_H+4, col);
}

void drawEditCursor(bool on) {
  if (!gUiEditMode) return;
  if (selectedCol >= COLS) return;
  if (selectedRow < scrollIndex || selectedRow >= scrollIndex + VISIBLE_ROWS) return;

  int x, y;
  cellXY(selectedRow, selectedCol, x, y);
  uint16_t col = on ? TFT_ORANGE : TFT_BLACK;
  tft.drawRect(x+1, y+1, CELL_W-4, CELL_H-4, col);
  tft.drawRect(x+2, y+2, CELL_W-6, CELL_H-6, col);
}

void redrawVisibleWindow() {
  for (int r = 0; r < VISIBLE_ROWS; r++) {
    int rowIndex = scrollIndex + r;
    if (rowIndex >= ROWS) break;

    bool hl = gHighlightStep ? (rowIndex == step) : (rowIndex == selectedRow);
    drawMatrixRow(rowIndex, hl);
  }
  drawScrollBar();
  drawEditCursor(true);
  if (gUiEditMode && selectedCol == COLS) {
    drawBpmCursor(true);
  } else {
    drawBpmCursor(false);
  }
}

void updateHighlight(int prev, int cur) {
  if (prev != -1) {
    bool hlPrev = gHighlightStep ? (prev == step) : (prev == selectedRow);
    drawMatrixRow(prev, hlPrev);
  }
  bool hlCur = gHighlightStep ? (cur == step) : (cur == selectedRow);
  drawMatrixRow(cur, hlCur);
  drawEditCursor(true);
}

void drawStatus(const char* modeTxt) {
  const int boxX = statusBoxX();
  const int boxY = statusBoxY();
  const int boxW = STATUS_W;
  const int boxH = STATUS_H;

  tft.fillRect(boxX, boxY, boxW, boxH, TFT_BLACK);

  tft.setTextFont(1);
  tft.setTextSize(1);
  tft.setTextColor(TFT_YELLOW, TFT_BLACK);

  uint8_t oldDatum = tft.getTextDatum();
  tft.setTextDatum(TR_DATUM);

  tft.drawString(modeTxt, boxX + boxW - 1, boxY + 0, 1);
  tft.drawString(String("BPM ") + String(BPM), boxX + boxW - 1, boxY + 9, 1);

  if (gUiEditMode) {
    if (selectedCol == COLS) {
      tft.setTextColor(TFT_ORANGE, TFT_BLACK);
      tft.drawString("C BPM", boxX + boxW - 1, boxY + 18, 1);
      drawBpmCursor(true);
    } else {
      tft.setTextColor(TFT_CYAN, TFT_BLACK);
      tft.drawString(String("C ") + String(selectedCol), boxX + boxW - 1, boxY + 18, 1);
      drawBpmCursor(false);
    }
  } else {
    drawBpmCursor(false);
  }

  tft.setTextDatum(oldDatum);
}

// ================================
//      PATRÓN POR DEFECTO
// ================================
static const uint8_t DEFAULT_PATTERN[][COLS] = {
  { 12,  72,  64,  60 },  // 0
  {  0,   0,   0,   0 },  // 1
  { 12,  76,  67,   0 },  // 2
  {  0,   0,   0,   0 },  // 3
  { 12,  79,  72,  55 },  // 4
  {  0,   0,   0,   0 },  // 5
  { 12,  84,  76,   0 },  // 6
  {  0,   0,   0,   0 },  // 7
  { 12,  79,  72,  60 },  // 8
  {  0,   0,   0,   0 },  // 9
  { 12,  76,  67,   0 },  // 10
  {  0,   0,   0,   0 },  // 11
  { 12,  72,  64,  55 },  // 12
  {  0,   0,   0,   0 },  // 13
  {  0,   0,   0,  60 },  // 14
  {  0,   0,   0,   0 },  // 15
};

constexpr int PATTERN_ROWS = sizeof(DEFAULT_PATTERN) / sizeof(DEFAULT_PATTERN[0]);

// ================================
//     ENCODERS (lectura)
// ================================
int readEncDelta(EncState& st, int pinCLK, int pinDT) {
  int delta = 0;
  int clk = digitalRead(pinCLK);
  unsigned long now = millis();

  if (st.prevCLK == HIGH && clk == LOW) {
    if (now - st.lastEdgeMs >= ENC_DEBOUNCE_MS) {
      st.lastEdgeMs = now;

      int dir = (digitalRead(pinDT) != clk) ? +1 : -1;

      if (st.lastDir != 0 && dir != st.lastDir && (now - st.lastTickMs) < ENC_GUARD_MS) {
        // ignore
      } else {
        delta = dir;
        st.lastDir = dir;
        st.lastTickMs = now;
      }
    }
  }
  st.prevCLK = clk;
  return delta;
}

bool readEncButton(EncState& st, int pinSW) {
  unsigned long now = millis();
  int state = digitalRead(pinSW);

  if (state == LOW) {
    if ((now - st.lastBtnMs) > BTN_DEBOUNCE_MS) {
      st.lastBtnMs = now;
      return true;
    }
  }
  return false;
}

// ================================
//        WIFI / MQTT helpers
// ================================
void connectWiFi() {
  Serial.println("Conectando WiFi...");
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  while (WiFi.status() != WL_CONNECTED) {
    vTaskDelay(pdMS_TO_TICKS(350));
    Serial.print(".");
  }
  Serial.print("\nWiFi OK. IP: ");
  Serial.println(WiFi.localIP());
}

static inline int bpmFromMs(int ms) {
  if (ms <= 0) return BPM;
  float bpmf = 60000.0f / (4.0f * (float)ms);
  int ibpm = (int)roundf(bpmf);
  if (ibpm < BPM_MIN) ibpm = BPM_MIN;
  if (ibpm > BPM_MAX) ibpm = BPM_MAX;
  return ibpm;
}

// ================================
//         HELPERS DE AUDIO
// ================================
static inline float midiToHz(uint8_t midi) {
  if (midi == 0) return 0.0f;
  if (midi > 127) midi = 127;
  return 440.0f * powf(2.0f, ((int)midi - 69) / 12.0f);
}

static inline void recalcSamplesPerStep() {
  samplesPerStep = (int)roundf(I2S_SR * 60.0f / (BPM * 4.0f)); // 4 steps por beat
}

void triggerStep(int s) {
  int dval = matrixVals[s][0] & 0x0F;
  enum DrumType : uint8_t { DR_NONE=0, DR_HAT=1, DR_SNARE=2, DR_KICK=3 };
  struct DrumPreset { DrumType type; uint8_t periodIdx; bool shortMode; };
  static const DrumPreset DRUM_PRESET[16] = {
    {DR_NONE,0,true},{DR_HAT,0,true},{DR_HAT,2,true},{DR_HAT,4,true},
    {DR_SNARE,8,false},{DR_SNARE,10,false},{DR_SNARE,12,false},{DR_KICK,15,false},
    {DR_KICK,14,false},{DR_KICK,13,false},{DR_HAT,1,true},{DR_SNARE,9,false},
    {DR_HAT,3,true},{DR_SNARE,11,false},{DR_KICK,15,false},{DR_HAT,5,true},
  };
  const DrumPreset& P = DRUM_PRESET[dval];
  if (P.type != DR_NONE) {
    envNoise = 1.0f;
    nNoise.setShort(P.shortMode);
    nNoise.setNote(P.periodIdx);
  }
  sq1.setFreq(midiToHz((uint8_t)matrixVals[s][1]));
  sq2.setFreq(midiToHz((uint8_t)matrixVals[s][2]));
  tri.setFreq(midiToHz((uint8_t)matrixVals[s][3]));
}

static void i2sWriteStereoSamples(const int16_t* data, size_t frames) {
  size_t wr = 0;
  i2s_write(I2S_PORT, (const char*)data, frames * 2 * sizeof(int16_t), &wr, portMAX_DELAY);
}

static void i2sInit() {
  i2s_config_t cfg = {
    .mode = (i2s_mode_t)(I2S_MODE_MASTER | I2S_MODE_TX),
    .sample_rate = I2S_SR,
    .bits_per_sample = I2S_BITS_PER_SAMPLE_16BIT,
    .channel_format = I2S_CHANNEL_FMT_RIGHT_LEFT,
    .communication_format = I2S_COMM_FORMAT_I2S,
    .intr_alloc_flags = 0,
    .dma_buf_count = 12,
    .dma_buf_len = 512,
    .use_apll = true,
    .tx_desc_auto_clear = true,
    .fixed_mclk = 0
  };
  i2s_pin_config_t pins = {
    .bck_io_num=PIN_BCLK,
    .ws_io_num=PIN_LRCK,
    .data_out_num=PIN_DOUT,
    .data_in_num=I2S_PIN_NO_CHANGE
  };
  i2s_driver_install(I2S_PORT, &cfg, 0, NULL);
  i2s_set_pin(I2S_PORT, &pins);
  i2s_set_clk(I2S_PORT, I2S_SR, I2S_BITS_PER_SAMPLE_16BIT, I2S_CHANNEL_STEREO);
}

// ================================
//   HELPERS MATRIZ (SYNC STRING)
// ================================
void applyMatrixFromString(const String& msg) {
  char buf[512];
  msg.toCharArray(buf, sizeof(buf));
  char* token = strtok(buf, " \t\r\n");

  int r = 0, c = 0;
  while (token != nullptr && r < ROWS) {
    int v = atoi(token);
    if (c == 0) {
      v = constrain(v, 0, 15);
    } else {
      v = constrain(v, 0, 127);
    }
    matrixVals[r][c] = v;

    c++;
    if (c >= COLS) {
      c = 0;
      r++;
    }
    token = strtok(nullptr, " \t\r\n");
  }

  while (r < ROWS) {
    while (c < COLS) {
      matrixVals[r][c] = 0;
      c++;
    }
    c = 0;
    r++;
  }

  Serial.println("Matrix actualizada desde Android (string)");
  gMatrixDirty = true; // <-- redibuja el loop, no acá
}

// Arma un string con toda la matriz y lo envía vía cola MQTT
void sendMatrixToAndroid() {
  String out;
  out.reserve(ROWS * COLS * 4);

  for (int i = 0; i < ROWS; i++) {
    for (int j = 0; j < COLS; j++) {
      out += String(matrixVals[i][j]);
      if (!(i == ROWS - 1 && j == COLS - 1)) {
        out += ' ';
      }
    }
  }
  Serial.println(out); 

  if (queueMqtt) {
    MqttCommand cmd;
    cmd.type = MQTT_CMD_MATRIX;
    strncpy(cmd.payload, out.c_str(), sizeof(cmd.payload) - 1);
    cmd.payload[sizeof(cmd.payload) - 1] = '\0';
    xQueueSend(queueMqtt, &cmd, 0);
  }

  Serial.println("Matrix enviada a Android (string)");
}

// ================================
//      MQTT CALLBACK
// ================================
void mqttCallback(char* topic, byte* payload, unsigned int length) {
  String msg;
  msg.reserve(length+1);
  for (unsigned int i=0;i<length;i++) msg += (char)payload[i];
  Serial.printf("MQTT [%s]: %s\n", topic, msg.c_str());

  if (!queueEvents) return;

  if (strcmp(topic, TOPIC_STATE) == 0) {
    Event ev = EV_NONE;

    // Normalizamos a mayúsculas para tolerar "PlayAll", "play_all", etc.
    String s = msg;
    s.trim();
    s.toUpperCase();

    if (s == "PLAY_ALL")       ev = EV_MQTT_TO_PLAY_ALL;
    else if (s == "IDLE")      ev = EV_MQTT_TO_IDLE;
    else if (s == "EDIT")      ev = EV_MQTT_TO_EDIT;
    else if (s == "PLAY_LINE") ev = EV_MQTT_TO_PLAY_LINE;

    if (ev != EV_NONE) {
      xQueueSend(queueEvents, &ev, 0);
    }
  }
  else if (strcmp(topic, TOPIC_TEMPO) == 0) {
    String s = msg;
    s.trim();
    int val = 0;
    bool isMs = false;
    if (s.endsWith("ms") || s.endsWith("MS") || s.endsWith("Ms")) {
      s.replace("ms",""); s.replace("MS",""); s.replace("Ms","");
      s.trim(); isMs = true;
    }
    val = s.toInt();

    int newBpm = BPM;
    if (isMs) {
      newBpm = bpmFromMs(val);
    } else {
      if (val >= BPM_MIN && val <= BPM_MAX) newBpm = val;
      else if (val >= 40 && val <= 1000)   newBpm = bpmFromMs(val);
    }

    if (newBpm != BPM) {
      gMqttTempoBpm = newBpm;
      Event ev = EV_MQTT_TEMPO_SET;
      xQueueSend(queueEvents, &ev, 0);
    }
  }
  else if (strcmp(topic, TOPIC_EDIT) == 0) {
    int r,c,v;
    if (sscanf(msg.c_str(), "%d %d %d", &r, &c, &v) == 3) {
      if (r>=0 && r<ROWS && c>=0 && c<COLS) {
        if (c==0) v = constrain(v, 0, 15);
        else      v = constrain(v, 0, 127);
        matrixVals[r][c] = v;
        gMatrixDirty = true; // <-- que redibuje el loop
      }
    }
  }
  else if (strcmp(topic, TOPIC_PLAY_ROW) == 0) {
    int r = msg.toInt();
    if (r >= 0 && r < ROWS) {
      gMqttPlayRow = r;
      Event ev = EV_MQTT_PLAY_ROW;
      xQueueSend(queueEvents, &ev, 0);
    }
  }
  else if (strcmp(topic, TOPIC_GET_CELL) == 0) {
    // Dos usos:
    // - "SEND_MATRIX" => Android pide que mandemos la matriz actual.
    // - Cualquier otro string => interpretamos como matriz completa para cargar.
    String s = msg;
    s.trim();
    if (s == "SEND_MATRIX") {
      Serial.println("MQTT: pedido de matriz desde Android, enviando matriz actual");
      sendMatrixToAndroid();
    } else {
      applyMatrixFromString(s);
    }
  }
}

void connectMQTT() {
  mqttClient.setServer(MQTT_BROKER, MQTT_PORT);
  mqttClient.setCallback(mqttCallback);
  while (!mqttClient.connected()) {
    Serial.print("Conectando a MQTT...");
    if (mqttClient.connect(MQTT_CLIENT_ID)) {
      Serial.println("Broker OK!");
      mqttClient.subscribe(TOPIC_STATE);
      mqttClient.subscribe(TOPIC_TEMPO);
      mqttClient.subscribe(TOPIC_EDIT);
      mqttClient.subscribe(TOPIC_PLAY_ROW);
      mqttClient.subscribe(TOPIC_GET_CELL);
      mqttClient.publish(TOPIC_STATUS, "IDLE");
    } else {
      Serial.print("rc=");
      Serial.println(mqttClient.state());
      vTaskDelay(pdMS_TO_TICKS(1500));
    }
  }
}

// ================================
//       TASK DE INPUT (EVENTOS)
// ================================
void vInputTask(void *pvParameters) {
  for (;;) {
    int d1 = readEncDelta(enc1, ENC1_CLK, ENC1_DT);
    if (d1 != 0 && queueEvents) {
      Event ev = (d1 > 0) ? EV_ENC1_CW : EV_ENC1_CCW;
      xQueueSend(queueEvents, &ev, 0);
    }

    int d2 = readEncDelta(enc2, ENC2_CLK, ENC2_DT);
    if (d2 != 0 && queueEvents) {
      Event ev = (d2 > 0) ? EV_ENC2_CW : EV_ENC2_CCW;
      xQueueSend(queueEvents, &ev, 0);
    }

    if (readEncButton(enc1, ENC1_SW) && queueEvents) {
      Event ev = EV_ENC1_PRESS;
      xQueueSend(queueEvents, &ev, 0);
    }

    if (readEncButton(enc2, ENC2_SW) && queueEvents) {
      Event ev = EV_ENC2_PRESS;
      xQueueSend(queueEvents, &ev, 0);
    }

    vTaskDelay(pdMS_TO_TICKS(INPUT_TASK_PERIOD_MS));
  }
}

// ================================
//       TASK: MQTT (único dueño mqttClient)
// ================================
void vMqttTask(void *pvParameters) {
  for (;;) {
    if (!mqttClient.connected()) {
      connectMQTT();
    } else {
      mqttClient.loop();

      // Procesar publicaciones pendientes
      if (queueMqtt) {
        MqttCommand cmd;
        while (xQueueReceive(queueMqtt, &cmd, 0) == pdTRUE) {
          switch (cmd.type) {
            case MQTT_CMD_STATUS:
              mqttClient.publish(TOPIC_STATUS, cmd.payload);
              break;
            case MQTT_CMD_MATRIX:
              mqttClient.publish(TOPIC_CELL_VAL, cmd.payload);
              break;
          }
        }
      }
    }
    vTaskDelay(pdMS_TO_TICKS(MQTT_TASK_PERIOD_MS));
  }
}

// ================================
//           SETUP
// ================================
void setup() {
  Serial.begin(115200);

  // Métricas: arranca ventana de "reposo" desde el arranque
  initStats();
  gMetricsIdleDone      = false;
  gMetricsPlayAllActive = false;
  gMetricsIdleStartMs   = millis();

  // Inicializar matriz
  for (int r = 0; r < ROWS; ++r) {
    for (int c = 0; c < COLS; ++c) {
      if (r < PATTERN_ROWS) matrixVals[r][c] = DEFAULT_PATTERN[r][c];
      else                  matrixVals[r][c] = 0;
    }
  }

  pinMode(ENC1_CLK, INPUT_PULLUP);
  pinMode(ENC1_DT , INPUT_PULLUP);
  pinMode(ENC1_SW , INPUT_PULLUP);
  pinMode(ENC2_CLK, INPUT_PULLUP);
  pinMode(ENC2_DT , INPUT_PULLUP);
  pinMode(ENC2_SW , INPUT_PULLUP);

  enc1.prevCLK = digitalRead(ENC1_CLK);
  enc2.prevCLK = digitalRead(ENC2_CLK);
  enc1.lastEdgeMs = enc2.lastEdgeMs = millis();

  queueEvents = xQueueCreate(EVENT_QUEUE_LEN, sizeof(Event));
  if (!queueEvents) {
    Serial.println("ERROR: no se pudo crear queueEvents");
  } else {
    xTaskCreatePinnedToCore(
      vInputTask,
      "InputTask",
      INPUT_TASK_STACK,
      nullptr,
      INPUT_TASK_PRIO,
      &hInputTask,
      1
    );
  }

  queueMqtt = xQueueCreate(8, sizeof(MqttCommand));
  if (!queueMqtt) {
    Serial.println("ERROR: no se pudo crear queueMqtt");
  }

  tft.init();
  tft.setRotation(1);
  drawHeader();
  gUiEditMode    = false;
  gHighlightStep = false;
  redrawVisibleWindow();
  drawStatus("IDLE");

  i2sInit();
  nNoise.setShort(true);
  nNoise.setNote(0);

  recalcSamplesPerStep();
  triggerStep(step);

  connectWiFi();
  connectMQTT();

  xTaskCreatePinnedToCore(
    vMqttTask,
    "MqttTask",
    MQTT_TASK_STACK,
    nullptr,
    MQTT_TASK_PRIO,
    &hMqttTask,
    0
  );
}

// ================================
//             LOOP
// ================================
void loop() {
  static int16_t buf[AUDIO_FRAMES * 2];
  static int stepCounter = 0;

  // ============================
  //  MÉTRICAS: ventana de reposo
  // ============================
  if (!gMetricsIdleDone) {
    if (millis() - gMetricsIdleStartMs >= 10000UL) {
      Serial.println("========== METRICAS: SISTEMA EN REPOSO (10s) ==========");
      finishStats();
      gMetricsIdleDone = true;
    }
  }

  // Estados SOLO acá adentro
  enum SystemState : uint8_t { IDLE=0, EDIT=1, PLAY_ALL=2, PLAY_LINE=3 };
  static SystemState state = IDLE;
  static const char* STATE_LABELS[] = { "IDLE", "EDIT", "PLAY_ALL", "PLAY_LINE" };

  auto publishState = [&]() {
    if (!queueMqtt) return;
    MqttCommand cmd;
    cmd.type = MQTT_CMD_STATUS;
    strncpy(cmd.payload, STATE_LABELS[(int)state], sizeof(cmd.payload) - 1);
    cmd.payload[sizeof(cmd.payload) - 1] = '\0';
    xQueueSend(queueMqtt, &cmd, 0);
  };

  auto stopVoices = [&]() {
    sq1.setFreq(0); sq2.setFreq(0); tri.setFreq(0);
    envNoise = 0.0f;
  };

  auto ensureRowVisible = [&]() {
    int oldScroll = scrollIndex;
    if (selectedRow < scrollIndex) scrollIndex = selectedRow;
    if (selectedRow >= scrollIndex + VISIBLE_ROWS)
      scrollIndex = selectedRow - (VISIBLE_ROWS - 1);
    if (scrollIndex != oldScroll) {
      redrawVisibleWindow();
    } else {
      redrawVisibleWindow();
    }
  };

  auto moveSelectedRow = [&](int delta) {
    int prev = selectedRow;
    selectedRow = constrain(selectedRow + delta, 0, ROWS - 1);
    int oldScroll = scrollIndex;
    if (selectedRow < scrollIndex) scrollIndex = selectedRow;
    if (selectedRow >= scrollIndex + VISIBLE_ROWS)
      scrollIndex = selectedRow - (VISIBLE_ROWS - 1);
    if (scrollIndex != oldScroll) {
      redrawVisibleWindow();
    } else {
      updateHighlight(prev, selectedRow);
      drawScrollBar();
    }
    drawStatus(STATE_LABELS[(int)state]);
  };

  auto moveSelectedCol = [&](int delta) {
    int prevCol = selectedCol;
    selectedCol = constrain(selectedCol + delta, 0, COLS); // COLS = BPM virtual
    if (selectedCol != prevCol) {
      redrawVisibleWindow();
      drawStatus(STATE_LABELS[(int)state]);
    }
  };

  auto applyTempoChange = [&]() {
    BPM = constrain(gMqttTempoBpm, BPM_MIN, BPM_MAX);
    recalcSamplesPerStep();
    drawStatus(STATE_LABELS[(int)state]);
  };

  auto applyEnc1DeltaEditMode = [&](int d1) {
    if (selectedCol == COLS) {
      int prevBpm = BPM;
      BPM = constrain(BPM + d1 * BPM_STEP, BPM_MIN, BPM_MAX);
      if (BPM != prevBpm) {
        recalcSamplesPerStep();
        drawStatus(STATE_LABELS[(int)state]);
        drawBpmCursor(true);
      }
    } else {
      int prevVal = matrixVals[selectedRow][selectedCol];
      if (selectedCol == 0) {
        matrixVals[selectedRow][0] = constrain(prevVal + d1, 0, 15);
      } else {
        matrixVals[selectedRow][selectedCol] =
            constrain(prevVal + d1, 0, 127);
      }
      drawMatrixRow(selectedRow, false);
      drawEditCursor(true);
      drawStatus(STATE_LABELS[(int)state]);
    }
  };

  auto startPlayAll = [&]() {
    state           = PLAY_ALL;
    gUiEditMode     = false;
    gHighlightStep  = true;
    redrawVisibleWindow();
    drawStatus(STATE_LABELS[(int)state]);
    publishState();
  };

  auto stopPlayAllToIdle = [&]() {
    // Si estamos midiendo PLAY_ALL por MQTT, cerramos la ventana de métricas acá
    if (gMetricsPlayAllActive) {
      Serial.println("========== METRICAS: PLAY_ALL desde MQTT (fin) ==========");
      finishStats();
      gMetricsPlayAllActive = false;
    }

    state           = IDLE;
    gUiEditMode     = false;
    gHighlightStep  = false;
    stopVoices();
    redrawVisibleWindow();
    drawStatus(STATE_LABELS[(int)state]);
    publishState();
  };

  auto enterEdit = [&]() {
    state           = EDIT;
    gUiEditMode     = true;
    gHighlightStep  = false;
    ensureRowVisible();
    redrawVisibleWindow();
    drawStatus(STATE_LABELS[(int)state]);
    publishState();
  };

  auto goIdleFromEditOrPlayLine = [&]() {
    state           = IDLE;
    gUiEditMode     = false;
    gHighlightStep  = false;
    stopVoices();
    redrawVisibleWindow();
    drawStatus(STATE_LABELS[(int)state]);
    publishState();
  };

  auto startPlayLine = [&]() {
    state           = PLAY_LINE;
    gUiEditMode     = true;
    gHighlightStep  = false;
    triggerStep(selectedRow);
    previewSamplesLeft = samplesPerStep;
    redrawVisibleWindow();
    drawStatus(STATE_LABELS[(int)state]);
    publishState();
  };

  auto stopPlayLineToEdit = [&]() {
    previewSamplesLeft = 0;
    stopVoices();
    state           = EDIT;
    gUiEditMode     = true;
    gHighlightStep  = false;
    redrawVisibleWindow();
    drawStatus(STATE_LABELS[(int)state]);
    publishState();
  };

  auto playRowFromMqtt = [&]() {
    selectedRow = gMqttPlayRow;
    ensureRowVisible();
    startPlayLine();
  };

  // Si MQTT tocó la matriz, redibujamos acá (único hilo que usa el TFT)
  if (gMatrixDirty) {
    gMatrixDirty = false;
    redrawVisibleWindow();
    drawStatus(STATE_LABELS[(int)state]);
  }

  // 🎵 Helpers de audio
  auto synthFrame = [&]() -> float {
    float nN        = nNoise.tick();
    float mixDrums  = nN * envNoise * GAIN_NOISE * MASTER_DRUMS;
    envNoise       *= DECAY_NOISE;

    float s1        = sq1.tick();
    float s2        = sq2.tick();
    float t         = tri.tick();
    float mixSynth  = (s1 + s2 + t) * MASTER_SYNTH;

    return mixDrums + mixSynth;
  };

  auto renderSilence = [&]() {
    for (size_t i = 0; i < AUDIO_FRAMES; ++i) {
      int16_t y = 0;
      buf[2*i+0] = y;
      buf[2*i+1] = y;
    }
  };

  auto renderPlayAll = [&]() {
    for (size_t i = 0; i < AUDIO_FRAMES; ++i) {
      float mix = synthFrame();

      if (++stepCounter >= samplesPerStep) {
        stepCounter = 0;
        int prevStep = step;
        step = (step + 1) % STEPS;
        triggerStep(step);

        int oldScroll = scrollIndex;
        if (step < scrollIndex) scrollIndex = step;
        if (step >= scrollIndex + VISIBLE_ROWS)
          scrollIndex = step - (VISIBLE_ROWS - 1);

        if (scrollIndex != oldScroll) {
          redrawVisibleWindow();
        } else {
          updateHighlight(prevStep, step);
          drawScrollBar();
        }
        drawStatus(STATE_LABELS[(int)state]);
      }

      if (mix > 1.0f) mix = 1.0f;
      if (mix < -1.0f) mix = -1.0f;
      int16_t y = (int16_t)(mix * 32767.0f);
      buf[2*i+0] = y;
      buf[2*i+1] = y;
    }
  };

  auto renderPlayLine = [&]() {
    for (size_t i = 0; i < AUDIO_FRAMES; ++i) {
      float mix = synthFrame();

      if (previewSamplesLeft > 0) {
        previewSamplesLeft--;
        if (previewSamplesLeft == 0) {
          stopPlayLineToEdit();
        }
      }

      if (mix > 1.0f) mix = 1.0f;
      if (mix < -1.0f) mix = -1.0f;
      int16_t y = (int16_t)(mix * 32767.0f);
      buf[2*i+0] = y;
      buf[2*i+1] = y;
    }
  };

  switch (state) {
    case IDLE: {
      if (queueEvents) {
        Event ev;
        while (xQueueReceive(queueEvents, &ev, 0) == pdTRUE) {
          switch (ev) {
            case EV_ENC1_CW:   moveSelectedRow(+1); break;
            case EV_ENC1_CCW:  moveSelectedRow(-1); break;

            case EV_ENC1_PRESS:
              startPlayAll();
              break;

            case EV_ENC2_PRESS:
              enterEdit();
              break;

            case EV_MQTT_TO_PLAY_ALL:
              // Inicia ventana de métricas para PLAY_ALL disparado por MQTT
              if (!gMetricsPlayAllActive) {
                Serial.println("========== METRICAS: PLAY_ALL desde MQTT (inicio) ==========");
                initStats();
                gMetricsPlayAllActive = true;
              }
              startPlayAll();
              break;

            case EV_MQTT_TO_IDLE:
              stopPlayAllToIdle();
              break;

            case EV_MQTT_TO_EDIT:
              enterEdit();
              break;

            case EV_MQTT_TO_PLAY_LINE:
              startPlayLine();
              break;

            case EV_MQTT_PLAY_ROW:
              playRowFromMqtt();
              break;

            case EV_MQTT_TEMPO_SET:
              applyTempoChange();
              break;

            default:
              break;
          }
        }
      }

      renderSilence();
      break;
    }

    case EDIT: {
      if (queueEvents) {
        Event ev;
        while (xQueueReceive(queueEvents, &ev, 0) == pdTRUE) {
          switch (ev) {
            case EV_ENC1_CW:  applyEnc1DeltaEditMode(+1); break;
            case EV_ENC1_CCW: applyEnc1DeltaEditMode(-1); break;

            case EV_ENC2_CW:  moveSelectedCol(+1); break;
            case EV_ENC2_CCW: moveSelectedCol(-1); break;

            case EV_ENC1_PRESS:
              startPlayLine();
              break;

            case EV_ENC2_PRESS:
              goIdleFromEditOrPlayLine();
              break;

            case EV_MQTT_TO_IDLE:
              stopPlayAllToIdle();
              break;

            case EV_MQTT_TO_EDIT:
              enterEdit();
              break;

            case EV_MQTT_TO_PLAY_ALL:
              if (!gMetricsPlayAllActive) {
                Serial.println("========== METRICAS: PLAY_ALL desde MQTT (inicio) ==========");
                initStats();
                gMetricsPlayAllActive = true;
              }
              startPlayAll();
              break;

            case EV_MQTT_TO_PLAY_LINE:
              startPlayLine();
              break;

            case EV_MQTT_PLAY_ROW:
              playRowFromMqtt();
              break;

            case EV_MQTT_TEMPO_SET:
              applyTempoChange();
              break;

            default:
              break;
          }
        }
      }

      renderSilence();
      break;
    }

    case PLAY_ALL: {
      if (queueEvents) {
        Event ev;
        while (xQueueReceive(queueEvents, &ev, 0) == pdTRUE) {
          switch (ev) {
            case EV_ENC1_CW:   moveSelectedRow(+1); break;
            case EV_ENC1_CCW:  moveSelectedRow(-1); break;

            case EV_ENC1_PRESS:
              stopPlayAllToIdle();
              break;

            case EV_ENC2_PRESS:
              enterEdit();
              break;

            case EV_MQTT_TO_IDLE:
              stopPlayAllToIdle();
              break;

            case EV_MQTT_TO_PLAY_ALL:
              // Si vuelven a mandar PLAY_ALL por MQTT y ya estábamos midiendo,
              // no reiniciamos las métricas.
              if (!gMetricsPlayAllActive) {
                Serial.println("========== METRICAS: PLAY_ALL desde MQTT (inicio) ==========");
                initStats();
                gMetricsPlayAllActive = true;
              }
              startPlayAll();
              break;

            case EV_MQTT_TO_EDIT:
              enterEdit();
              break;

            case EV_MQTT_TO_PLAY_LINE:
              startPlayLine();
              break;

            case EV_MQTT_PLAY_ROW:
              playRowFromMqtt();
              break;

            case EV_MQTT_TEMPO_SET:
              applyTempoChange();
              break;

            default:
              break;
          }
        }
      }

      renderPlayAll();
      break;
    }

    case PLAY_LINE: {
      if (queueEvents) {
        Event ev;
        while (xQueueReceive(queueEvents, &ev, 0) == pdTRUE) {
          switch (ev) {
            case EV_ENC1_CW:  applyEnc1DeltaEditMode(+1); break;
            case EV_ENC1_CCW: applyEnc1DeltaEditMode(-1); break;

            case EV_ENC2_CW:  moveSelectedCol(+1); break;
            case EV_ENC2_CCW: moveSelectedCol(-1); break;

            case EV_ENC1_PRESS:
              stopPlayLineToEdit();
              break;

            case EV_ENC2_PRESS:
              goIdleFromEditOrPlayLine();
              break;

            case EV_MQTT_TO_IDLE:
              stopPlayAllToIdle();
              break;

            case EV_MQTT_TO_EDIT:
              enterEdit();
              break;

            case EV_MQTT_TO_PLAY_ALL:
              if (!gMetricsPlayAllActive) {
                Serial.println("========== METRICAS: PLAY_ALL desde MQTT (inicio) ==========");
                initStats();
                gMetricsPlayAllActive = true;
              }
              startPlayAll();
              break;

            case EV_MQTT_TO_PLAY_LINE:
              startPlayLine();
              break;

            case EV_MQTT_PLAY_ROW:
              playRowFromMqtt();
              break;

            case EV_MQTT_TEMPO_SET:
              applyTempoChange();
              break;

            default:
              break;
          }
        }
      }

      renderPlayLine();
      break;
    }
  }

  i2sWriteStereoSamples(buf, AUDIO_FRAMES);
}