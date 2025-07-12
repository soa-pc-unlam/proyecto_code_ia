/**
 * Smart Home Temperature Control System for ESP32
 * 
 * This program implements a state machine to control a smart home temperature system
 * with both automatic and manual modes. It uses FreeRTOS tasks for concurrent operations.
 */

#include <Arduino.h>
#include <freertos/FreeRTOS.h>
#include <freertos/task.h>
#include <freertos/timers.h>

// Pin definitions
const int TEMP_SENSOR_PIN = 34;    // Analog temperature sensor
const int BUTTON1_PIN = 25;         // Mode toggle button
const int BUTTON2_PIN = 26;        // Manual control button
const int RELAY_PIN = 2;          // Relay control for fan
const int BUZZER_PIN = 4;         // Buzzer for alerts

// Temperature thresholds (in raw ADC values - adjust as needed)
const int TEMP_HIGH_THRESHOLD = 30;  // °C to turn on fan
const int TEMP_LOW_THRESHOLD = 25;   // °C to turn off fan

// System states
typedef enum {
  STATE_MANUAL,
  STATE_AUTO,
  STATE_ALERT
} SystemState;

// Event types
typedef enum {
  EVENT_NONE,
  EVENT_BUTTON1_PRESS,
  EVENT_BUTTON2_PRESS,
  EVENT_TEMP_HIGH,
  EVENT_TEMP_LOW,
  EVENT_TIMER
} EventType;

// Global variables
volatile SystemState currentState = STATE_AUTO;  // Start in auto mode
volatile bool fanState = false;
volatile float currentTemp = 0.0;
volatile bool button1State = false;
volatile bool button2State = false;
volatile bool lastButton1State = false;
volatile bool lastButton2State = false;

// Function prototypes
void readSensorsTask(void *parameter);
void controlTask(void *parameter);
void playMelody();
void updateDisplay();
EventType get_input();
float readTemperature();
void controlFan(bool state);

// Task handles
TaskHandle_t readSensorsTaskHandle = NULL;
TaskHandle_t controlTaskHandle = NULL;

// Melody notes (frequencies in Hz)
const int melody[] = {262, 294, 330, 349, 392, 440, 494, 523};
const int noteDurations[] = {4, 8, 8, 4, 4, 4, 4, 4};

void setup() {
  // Initialize serial communication
  Serial.begin(115200);
  
  // Initialize pins
  pinMode(TEMP_SENSOR_PIN, INPUT);
  pinMode(BUTTON1_PIN, INPUT_PULLUP);
  pinMode(BUTTON2_PIN, INPUT_PULLUP);
  pinMode(RELAY_PIN, OUTPUT);
  pinMode(BUZZER_PIN, OUTPUT);
  
  // Ensure relay is off initially
  digitalWrite(RELAY_PIN, LOW);
  
  // Create tasks
  xTaskCreatePinnedToCore(
    readSensorsTask,    // Task function
    "ReadSensors",      // Task name
    4096,               // Stack size (bytes)
    NULL,               // Task parameters
    1,                  // Task priority
    &readSensorsTaskHandle, // Task handle
    1                   // Core (1)
  );
  
  xTaskCreatePinnedToCore(
    controlTask,        // Task function
    "Control",          // Task name
    4096,               // Stack size (bytes)
    NULL,               // Task parameters
    2,                  // Task priority (higher than readSensors)
    &controlTaskHandle, // Task handle
    1                   // Core (1)
  );
  
  Serial.println("Smart Home Temperature Control System Started");
  Serial.println("Initial State: AUTO");
}

void loop() {
  // Main loop is empty as we're using FreeRTOS tasks
  vTaskDelay(pdMS_TO_TICKS(1000));
}

/**
 * Task to read sensors and update system state
 */
void readSensorsTask(void *parameter) {
  while (1) {
    // Read temperature
    currentTemp = readTemperature();
    
    // Read buttons with debounce
    bool currentButton1 = !digitalRead(BUTTON1_PIN);
    bool currentButton2 = !digitalRead(BUTTON2_PIN);
    
    if (currentButton1 != lastButton1State) {
      vTaskDelay(pdMS_TO_TICKS(50)); // Debounce delay
      button1State = currentButton1;
      lastButton1State = currentButton1;
    }
    
    if (currentButton2 != lastButton2State) {
      vTaskDelay(pdMS_TO_TICKS(50)); // Debounce delay
      button2State = currentButton2;
      lastButton2State = currentButton2;
    }
    
    vTaskDelay(pdMS_TO_TICKS(100)); // Task delay
  }
}

/**
 * Task to handle state machine and control logic
 */
void controlTask(void *parameter) {
  while (1) {
    EventType event = get_input();
    
    // State machine with nested switch as required
    switch (currentState) {
      case STATE_MANUAL:
        switch (event) {
          case EVENT_BUTTON1_PRESS:
            currentState = STATE_AUTO;
            Serial.println("State changed: MANUAL -> AUTO");
            break;
            
          case EVENT_BUTTON2_PRESS:
            fanState = !fanState;
            controlFan(fanState);
            if (fanState) {
              playMelody();
              Serial.println("Fan turned ON manually");
            } else {
              Serial.println("Fan turned OFF manually");
            }
            break;
            
          default:
            break;
        }
        break;
        
      case STATE_AUTO:
        switch (event) {
          case EVENT_BUTTON1_PRESS:
            currentState = STATE_MANUAL;
            Serial.println("State changed: AUTO -> MANUAL");
            break;
            
          case EVENT_TEMP_HIGH:
            if (!fanState) {
              fanState = true;
              controlFan(true);
              playMelody();
              Serial.println("Fan turned ON (AUTO - High temp)");
            }
            break;
            
          case EVENT_TEMP_LOW:
            if (fanState) {
              fanState = false;
              controlFan(false);
              Serial.println("Fan turned OFF (AUTO - Low temp)");
            }
            break;
            
          default:
            break;
        }
        break;
        
      case STATE_ALERT:
        // Additional state for future expansion (e.g., critical temperature)
        switch (event) {
          case EVENT_BUTTON1_PRESS:
            currentState = STATE_MANUAL;
            Serial.println("State changed: ALERT -> MANUAL");
            break;
            
          default:
            break;
        }
        break;
    }
    
    vTaskDelay(pdMS_TO_TICKS(50)); // Small delay to prevent task starvation
  }
}

/**
 * Read and process input events
 */
EventType get_input() {
  static unsigned long lastButton1Press = 0;
  static unsigned long lastButton2Press = 0;
  const unsigned long debounceTime = 300; // ms
  
  // Check for button presses
  if (button1State && (millis() - lastButton1Press) > debounceTime) {
    lastButton1Press = millis();
    return EVENT_BUTTON1_PRESS;
  }
  
  if (button2State && (millis() - lastButton2Press) > debounceTime) {
    lastButton2Press = millis();
    return EVENT_BUTTON2_PRESS;
  }
  
  // Check temperature conditions (only in AUTO mode)
  if (currentState == STATE_AUTO) {
    if (currentTemp > TEMP_HIGH_THRESHOLD) {
      return EVENT_TEMP_HIGH;
    } else if (currentTemp < TEMP_LOW_THRESHOLD) {
      return EVENT_TEMP_LOW;
    }
  }
  
  return EVENT_NONE;
}

/**
 * Read temperature from analog sensor
 */
float readTemperature() {
  // Read analog value (0-4095 for ESP32 ADC)
  int rawValue = analogRead(TEMP_SENSOR_PIN);
  
  // Convert ADC value to voltage (0-3.3V)
  float voltage = rawValue * (3.3 / 4095.0);
  
  // Convert voltage to temperature (example for LM35: 10mV per °C)
  // Adjust this formula based on your specific temperature sensor
  float temperature = voltage * 100.0; // For LM35
  
  return temperature;
}

/**
 * Control fan relay
 */
void controlFan(bool state) {
  digitalWrite(RELAY_PIN, state ? HIGH : LOW);
  fanState = state;
}

/**
 * Play a simple melody on the buzzer
 */
void playMelody() {
  for (int i = 0; i < 8; i++) {
    int noteDuration = 1000 / noteDurations[i];
    tone(BUZZER_PIN, melody[i], noteDuration);
    
    // To distinguish the notes, set a minimum time between them
    int pauseBetweenNotes = noteDuration * 1.30;
    delay(pauseBetweenNotes);
    
    // Stop the tone playing
    noTone(BUZZER_PIN);
    
    // Small delay to prevent task blocking
    vTaskDelay(1);
  }
}

/**
 * Update display (placeholder for future implementation)
 */
void updateDisplay() {
  // This function can be expanded to update an OLED/LCD display
  // showing current mode, temperature, and fan status
}
