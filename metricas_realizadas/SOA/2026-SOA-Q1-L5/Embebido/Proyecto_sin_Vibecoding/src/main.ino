#include <Arduino.h>
#include <WiFi.h>
#include <freertos/FreeRTOS.h>
#include <freertos/task.h>

#include "enums.h"
#include "pins.h"
#include "structs.h"

#include "constants.h"

#include "user_functions.h"

#include "sync.h"
#include "tasks.h"

#include "mqtt.h"

#include "event_captures.h"

#include "debuggers.h"

SystemStatus systemStatus = VIRGIN_EMBEDDED;

const QueueHandle_t xSystemEventsQueue = xQueueCreate(10, sizeof(unsigned long));

static void xSystemEventTask(void* parameters) {
    const unsigned int eventsLength = 4;
    const SystemEvent (*events[eventsLength])(SystemStatus status) = {
        getSecurityBtnEvent,
        getAnomalySensorEvent,
        getStockBtnEvent,
        getStockSensorEvent,
    };

    int i = 0;
    SystemEvent newEvent;

    const int delay = pdMS_TO_TICKS(150);

    while (true) {
        i = i % eventsLength;
        newEvent = events[i](systemStatus);

        xQueueSend(xSystemEventsQueue, &newEvent, 0);
        vTaskDelay(delay);

        i++;
    }
}

static void xFSMTask(void* parameters) {
    SystemEvent event;
    const TickType_t waitTime = portMAX_DELAY;

    while (true) {
        xQueueReceive(xSystemEventsQueue, &event, waitTime);

        switch (systemStatus) {
            case SystemStatus::VIRGIN_EMBEDDED:
                switch (event) {
                    case SystemEvent::STOCK_ON:
                        lcdClear(&LCD);
                        systemStatus = SystemStatus::STOCK_MODE;
                        DEBUG_FSM(SystemStatus::VIRGIN_EMBEDDED, event, systemStatus);
                        break;

                    case SystemEvent::SECURITY_ON:
                        lcdPrint(&LCD, "Security mode");
                        setBaselineWeight(&weightSensor01);
                        systemStatus = SystemStatus::SECURITY_MODE;
                        DEBUG_FSM(SystemStatus::VIRGIN_EMBEDDED, event, systemStatus);
                        break;

                    default:
                        lcdPrint(&LCD, "SOA - Team L5", "S.S. control");
                        DEBUG_FSM(SystemStatus::VIRGIN_EMBEDDED, event, SystemStatus::VIRGIN_EMBEDDED);
                        break;
                }
                break;

            case SystemStatus::STOCK_MODE:
                switch (event) {
                    case SystemEvent::STOCK_OFF:
                        ledOff(&weightSensor01);
                        lcdClear(&LCD);
                        systemStatus = SystemStatus::VIRGIN_EMBEDDED;
                        DEBUG_FSM(SystemStatus::STOCK_MODE, event, systemStatus);
                        break;

                    case SystemEvent::STOCK_MISSING_SENSOR_01:
                        ledOn(&weightSensor01);
                        lcdPrint(&LCD, "Stock missing", "on sensor!");
                        DEBUG_FSM(SystemStatus::STOCK_MODE, event, systemStatus);
                        break;

                    case SystemEvent::NO_MISSING_STOCK:
                        ledOff(&weightSensor01);
                        lcdPrint(&LCD, "Stock = " + String(getStock(&weightSensor01)), "");
                        DEBUG_FSM(SystemStatus::STOCK_MODE, event, systemStatus);
                        break;

                    case SystemEvent::SECURITY_ON:
                        setBaselineWeight(&weightSensor01);
                        ledOff(&weightSensor01);
                        lcdPrint(&LCD, "Security mode");
                        systemStatus = SystemStatus::SECURITY_MODE;
                        DEBUG_FSM(SystemStatus::STOCK_MODE, event, systemStatus);
                        break;

                    default:
                        break;
                }
                break;

            case SystemStatus::SECURITY_MODE:
                switch (event) {
                    case SystemEvent::SECURITY_OFF:
                        stopBuzzer(&buzzer);
                        ledOff(&weightSensor01);
                        lcdClear(&LCD);
                        systemStatus = SystemStatus::VIRGIN_EMBEDDED;
                        DEBUG_FSM(SystemStatus::SECURITY_MODE, event, systemStatus);
                        break;

                    case SystemEvent::SECURITY_OFF_TO_STOCK:
                        stopBuzzer(&buzzer);
                        ledOff(&weightSensor01);
                        lcdClear(&LCD);
                        systemStatus = SystemStatus::STOCK_MODE;
                        DEBUG_FSM(SystemStatus::SECURITY_MODE, event, systemStatus);
                        break;

                    case SystemEvent::ANOMALY_SENSOR_01:
                        ledOn(&weightSensor01);
                        playBuzzer(&buzzer);
                        lcdPrint(&LCD, "Security alert", "on all sensors!");
                        DEBUG_FSM(SystemStatus::SECURITY_MODE, event, systemStatus);
                        break;

                    default:
                        DEBUG_FSM(SystemStatus::SECURITY_MODE, event, systemStatus);
                        break;
                }
                break;

            default:
                break;
        }
    }
}

void setup() {
    Serial.begin(57600);

    DEBUG("\r\nStarting setup...\r\n\n");

    pinMode(stockBtn.pin, INPUT);
    pinMode(stockBtn.ledPin, OUTPUT);
    digitalWrite(stockBtn.ledPin, LOW);

    stockBtn.state = HIGH;
    stockBtn.status = ButtonStatus::OFF;
    stockBtn.lastState = HIGH;
    stockBtn.lastDebounceTime = 0;

    DEBUG_BUTTON("stockBtn", stockBtn);
    DEBUG("\r\n");

    pinMode(securityBtn.pin, INPUT);
    pinMode(securityBtn.ledPin, OUTPUT);
    digitalWrite(securityBtn.ledPin, LOW);

    securityBtn.state = HIGH;
    securityBtn.status = ButtonStatus::OFF;
    securityBtn.lastState = HIGH;
    securityBtn.lastDebounceTime = 0;

    DEBUG_BUTTON("securityBtn", securityBtn);
    DEBUG("\r\n");

    LCD.device->begin(LCD_COLS, LCD_ROWS);
    LCD.device->setRGB(255, 255, 255);

    pinMode(buzzer.pin, OUTPUT);

    weightSensor01.device.begin(weightSensor01.dtPin, weightSensor01.sckPin);
    weightSensor01.device.set_scale(WEIGHT_SENSORS_CALIBRATION_FACTOR);

    tare(&weightSensor01);
    pinMode(weightSensor01.ledPin, OUTPUT);

    DEBUG_WEIGHT_SENSOR("weightSensor01", weightSensor01);
    DEBUG("\r\n");

    WiFi.mode(WIFI_STA);
    WiFi.disconnect();

    delay(150);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    delay(1000);

    WiFi.onEvent(
        [](arduino_event_id_t event, arduino_event_info_t info) { WiFi.begin(WIFI_SSID, WIFI_PASSWORD); },
        ARDUINO_EVENT_WIFI_STA_DISCONNECTED
    );

    DEBUG_WIFI();
    DEBUG("\r\n\n");

    mqttClient.setServer(MQTT_BROKER_HOST, MQTT_BROKER_PORT);
    mqttClient.setKeepAlive(MQTT_KEEPALIVE);
    mqttClient.setCallback(mqttCallback);

    initMutexs();

    lockWeightSensors();
    setWeight(&weightSensor01);
    unlockWeightSensors();

    xTaskCreatePinnedToCore(xButtonsTask, "Buttons", 2048, NULL, 1, NULL, 1);
    xTaskCreatePinnedToCore(xWeightSampleTask, "WeightSample", 2048, NULL, 2, NULL, 1);
    xTaskCreatePinnedToCore(xBuzzerTask, "Alarm", 2048, &buzzer, 2, NULL, 1);
    xTaskCreatePinnedToCore(xMQTTTask, "MQTT", 4096, NULL, 3, NULL, 1);

    xTaskCreatePinnedToCore(xSystemEventTask, "SystemEvent", 2048, NULL, 4, NULL, 1);
    xTaskCreatePinnedToCore(xFSMTask, "FSM", 2048, NULL, 4, NULL, 1);

    DEBUG("Setup completed.\r\n\n");
}

void loop() {
}
