#ifndef DEBUGGERS_H_INCLUDED
#define DEBUGGERS_H_INCLUDED

#include "enums.h"

#define DEBUG_MODE false

#if DEBUG_MODE

#define DEBUG(message, ...) Serial.printf(message, ##__VA_ARGS__);

#define DEBUG_SIGNAL(signal, printMethod) printMethod(signal ? "HIGH" : "LOW");

#define DEBUG_BUTTON(name, button)                                            \
    Serial.printf("%s.pin = %u\r\n", name, button.pin);                       \
    Serial.printf("%s.ledPin = %u\r\n", name, button.ledPin);                 \
    Serial.printf("%s.state = ", name);                                       \
    DEBUG_SIGNAL(button.state, Serial.println);                               \
    Serial.printf("%s.status = \r\n", name, toString(button.status));         \
    Serial.printf("%s.lastState = ", name);                                   \
    DEBUG_SIGNAL(button.lastState, Serial.println);                           \
    Serial.printf("%s.debounceDelay = %ums\r\n", name, button.debounceDelay); \
    Serial.printf("%s.lastDebounceTime = %ums\r\n", name, button.lastDebounceTime);

#define DEBUG_WEIGHT_SENSOR(name, sensor)                     \
    Serial.printf("%s.id = %s\r\n", name, sensor.id.c_str()); \
    Serial.printf("%s.device = %p\r\n", name, sensor.device); \
    Serial.printf("%s.dtPin = %u\r\n", name, sensor.dtPin);   \
    Serial.printf("%s.sckPin = %u\r\n", name, sensor.sckPin); \
    Serial.printf("%s.ledPin = %u\r\n", name, sensor.ledPin);

#define DEBUG_WIFI()                                                                        \
    if (WiFi.status() == WL_CONNECTED) {                                                    \
        Serial.printf("WiFi connected (local IP: %s).", WiFi.localIP().toString().c_str()); \
    } else {                                                                                \
        Serial.println("WiFi not connected.");                                              \
    }

#define DEBUG_SYSTEM_STATUS(status, printMethod)  \
    switch (status) {                             \
        case SystemStatus::VIRGIN_EMBEDDED:       \
            printMethod("VIRGIN_EMBEDDED      "); \
            break;                                \
                                                  \
        case SystemStatus::STOCK_MODE:            \
            printMethod("STOCK_MODE           "); \
            break;                                \
                                                  \
        case SystemStatus::SECURITY_MODE:         \
            printMethod("SECURITY_MODE        "); \
            break;                                \
                                                  \
        default:                                  \
            printMethod("UNKNOWN_SYSTEM_STATUS"); \
    }

#define DEBUG_SYSTEM_EVENT(event, printMethod)         \
    switch (event) {                                   \
        case SystemEvent::STOCK_ON:                    \
            printMethod("STOCK_ON               ");    \
            break;                                     \
                                                       \
        case SystemEvent::STOCK_OFF:                   \
            printMethod("STOCK_OFF              ");    \
            break;                                     \
                                                       \
        case SystemEvent::STOCK_MISSING_SENSOR_01:     \
            printMethod("STOCK_MISSING_SENSOR_01   "); \
            break;                                     \
                                                       \
        case SystemEvent::NO_MISSING_STOCK:            \
            printMethod("NO_MISSING_STOCK       ");    \
            break;                                     \
                                                       \
        case SystemEvent::SECURITY_ON:                 \
            printMethod("SECURITY_ON            ");    \
            break;                                     \
                                                       \
        case SystemEvent::SECURITY_OFF:                \
            printMethod("SECURITY_OFF           ");    \
            break;                                     \
                                                       \
        case SystemEvent::SECURITY_OFF_TO_STOCK:       \
            printMethod("SECURITY_OFF_TO_STOCK  ");    \
            break;                                     \
                                                       \
        case SystemEvent::ANOMALY_SENSOR_01:           \
            printMethod("ANOMALY_SENSOR_01         "); \
            break;                                     \
                                                       \
        default:                                       \
            printMethod("UNKNOWN_SYSTEM_EVENT   ");    \
    }

#define DEBUG_FSM(status, event, nextStatus)   \
    DEBUG_SYSTEM_STATUS(status, Serial.print); \
    DEBUG_SYSTEM_EVENT(event, Serial.print);   \
    Serial.print(" --> ");                     \
    DEBUG_SYSTEM_STATUS(nextStatus, Serial.println);

#else

#define DEBUG(message, ...)                      ;
#define DEBUG_SIGNAL(signal, printMethod)        ;
#define DEBUG_BUTTON(name, button)               ;
#define DEBUG_WEIGHT_SENSOR(name, sensor)        ;
#define DEBUG_WIFI()                             ;
#define DEBUG_SYSTEM_STATUS(status, printMethod) ;
#define DEBUG_SYSTEM_EVENT(event, printMethod)   ;
#define DEBUG_FSM(status, event, nextStatus)     ;

#endif
#endif
