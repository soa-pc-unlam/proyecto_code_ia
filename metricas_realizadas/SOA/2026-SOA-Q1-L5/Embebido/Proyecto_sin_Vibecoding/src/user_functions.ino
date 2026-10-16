
#include "constants.h"
#include "structs.h"
#include "sync.h"
#include "user_functions.h"

void switchBtn(Button* btn) {
    const int read = digitalRead(btn->pin);
    if (read != btn->lastState) btn->lastDebounceTime = millis();

    if ((millis() - btn->lastDebounceTime) > btn->debounceDelay && read != btn->state) {
        btn->state = read;

        if (btn->state == HIGH) {
            const ButtonStatus newStatus = btn->status == ButtonStatus::ON ? ButtonStatus::OFF : ButtonStatus::ON;
            btn->status = newStatus;
            digitalWrite(btn->ledPin, newStatus);
        }
    }

    btn->lastState = read;
}

void lcdPrint(LCD16x2* lcd, const String line) {
    if ((lcd->line01 != "" && lcd->line01 != line) || lcd->line02 != "") lcd->device->clear();

    lcd->device->setCursor(0, 0);
    lcd->device->print(line);

    lcd->line01 = line;
    lcd->line02 = "";
}

void lcdPrint(LCD16x2* lcd, const String line01, const String line02) {
    if ((lcd->line01 != "" && lcd->line01 != line01) || (lcd->line02 != "" && lcd->line02 != line02)) {
        lcd->device->clear();
    }

    lcd->device->setCursor(0, 0);
    lcd->device->print(line01);

    lcd->device->setCursor(0, 1);
    lcd->device->print(line02);

    lcd->line01 = line01;
    lcd->line02 = line02;
}

void lcdClear(LCD16x2* lcd) {
    lcd->device->clear();
    lcd->line01 = "";
    lcd->line02 = "";
}

void playBuzzer(Buzzer* buzzer) {
    lockBuzzer();
    buzzer->playing = true;
    unlockBuzzer();
}

void stopBuzzer(Buzzer* buzzer) {
    lockBuzzer();
    buzzer->playing = false;
    unlockBuzzer();
}

void muteBuzzer(Buzzer* buzzer) {
    lockBuzzer();
    buzzer->muted = true;
    unlockBuzzer();
}

void unmuteBuzzer(Buzzer* buzzer) {
    lockBuzzer();
    buzzer->muted = false;
    unlockBuzzer();
}

const int32_t getOffset(WeightSensor* sensor) {
    lockWeightSensors();
    const int32_t offset = sensor->device.get_offset();
    unlockWeightSensors();

    return offset;
}

const unsigned int getWeight(const WeightSensor* sensor) {
    unsigned int weight = 0;

    lockWeightSensors();
    if (sensor->sample.isValid) weight = sensor->sample.weight;
    unlockWeightSensors();

    return weight;
}

const unsigned int getStock(const WeightSensor* sensor) {
    unsigned int stock = 0;

    lockWeightSensors();
    if (sensor->sample.isValid) stock = sensor->sample.weight / sensor->product.weight;
    unlockWeightSensors();

    return stock;
}

const String* getProductName(WeightSensor* sensor) {
    //
    return &sensor->product.name;
}

const unsigned int getMinimumAcceptableStock(const WeightSensor* sensor) {
    //
    return sensor->product.minimumAcceptableStock;
}

bool hasAnomaly(const WeightSensor* sensor) {
    bool anomaly = false;

    lockWeightSensors();
    if (sensor->sample.isValid) {
        unsigned int weight = weightSensor01.sample.weight;
        const unsigned int baselineWeight = weightSensor01.baselineWeight;
        anomaly = weightSensor01.anomaly || abs((int)weight - (int)baselineWeight) > ANOMALY_THRESHOLD;
    }
    unlockWeightSensors();

    return anomaly;
}

void setOffset(WeightSensor* sensor, int32_t offset) {
    lockWeightSensors();
    sensor->device.set_offset(offset);
    unlockWeightSensors();
}

void setWeight(WeightSensor* sensor) {
    if (!sensor->device.is_ready()) {
        sensor->sample.isValid = false;
        return;
    }

    const uint8_t times = 10;
    float weight = sensor->device.get_units(times);
    if (weight < 0.0f) weight = 0.0f;

    sensor->sample.weight = (unsigned int)floor(weight);
    sensor->sample.isValid = true;
}

void setBaselineWeight(WeightSensor* sensor) {
    lockWeightSensors();
    if (sensor->sample.isValid) sensor->baselineWeight = sensor->sample.weight;
    unlockWeightSensors();
}

void tare(WeightSensor* sensor) {
    lockWeightSensors();
    sensor->device.tare();
    unlockWeightSensors();
}

void ledOn(const WeightSensor* sensor) {
    if (digitalRead(sensor->ledPin) == HIGH) return;
    digitalWrite(sensor->ledPin, HIGH);
}

void ledOff(const WeightSensor* sensor) {
    if (digitalRead(sensor->ledPin) == LOW) return;
    digitalWrite(sensor->ledPin, LOW);
}
