#ifndef STRUCTS_H_INCLUDED
#define STRUCTS_H_INCLUDED

#include <Arduino.h>
#include <HX711.h>
#include <rgb_lcd.h>

#include "enums.h"

struct Button {
    const uint8_t pin;
    const uint8_t ledPin;
    int state;
    ButtonStatus status;
    int lastState;
    const unsigned long debounceDelay;
    unsigned long lastDebounceTime;
};

struct LCD16x2 {
    rgb_lcd* device;
    String line01;
    String line02;
};

struct Product {
    String name;
    unsigned int weight;
    unsigned int minimumAcceptableStock;
};

struct WeightSample {
    unsigned int weight;
    bool isValid;
};

struct WeightSensor {
    String id;
    HX711 device;
    const uint8_t dtPin;
    const uint8_t sckPin;
    const uint8_t ledPin;
    Product product;
    WeightSample sample;
    unsigned int baselineWeight;
    bool anomaly;
};

struct BuzzerStep {
    unsigned int frequency;
    unsigned long duration;
};

struct Buzzer {
    const uint8_t pin;
    const BuzzerStep* steps;
    const size_t stepsLength;
    bool muted;
    bool playing;
};

#endif
