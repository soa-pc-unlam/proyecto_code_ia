
#include <ArduinoJson.h>
#include <PubSubClient.h>
#include <WiFi.h>

#include "constants.h"
#include "debuggers.h"
#include "event_captures.h"
#include "mqtt.h"
#include "sync.h"
#include "user_functions.h"

WiFiClient espClient;
PubSubClient mqttClient(espClient);

static String topicHealth = String(MQTT_DEVICE_ID) + "/health";

static void publishTopicHealth() {
    JsonDocument payload;
    char payloadBuffer[32];

    payload["status"] = "online";
    serializeJson(payload, payloadBuffer, sizeof(payloadBuffer));

    mqttClient.publish(topicHealth.c_str(), payloadBuffer, true);
}

static void publishTopicSystem() {
    const String topic = String(MQTT_DEVICE_ID) + "/system";

    JsonDocument payload;
    char payloadBuffer[32];

    payload["status"] = toString(systemStatus);
    serializeJson(payload, payloadBuffer, sizeof(payloadBuffer));

    mqttClient.publish(topic.c_str(), payloadBuffer, true);
}

static void publishTopicAlarm() {
    const String topic = String(MQTT_DEVICE_ID) + "/alarm";

    JsonDocument payload;
    char payloadBuffer[64];

    payload["muted"] = buzzer.muted;
    payload["playing"] = buzzer.playing;
    serializeJson(payload, payloadBuffer, sizeof(payloadBuffer));

    mqttClient.publish(topic.c_str(), payloadBuffer, true);
}

static void publishTopicStock(WeightSensor* sensor) {
    const String topic = String(MQTT_DEVICE_ID) + "/" + sensor->id + "/stock";

    JsonDocument payload;
    char payloadBuffer[128];

    payload["name"] = *getProductName(sensor);
    payload["weight"] = getWeight(sensor);
    payload["weightPerUnit"] = sensor->product.weight;
    payload["stock"] = getStock(sensor);
    payload["minimumAcceptableStock"] = getMinimumAcceptableStock(sensor);
    serializeJson(payload, payloadBuffer, sizeof(payloadBuffer));

    mqttClient.publish(topic.c_str(), payloadBuffer, true);
}

static void publishTopicSecurity(const WeightSensor* sensor) {
    const String topic = String(MQTT_DEVICE_ID) + "/" + sensor->id + "/security";

    JsonDocument payload;
    char payloadBuffer[64];

    payload["weight"] = getWeight(sensor);
    payload["anomaly"] = hasAnomaly(sensor);
    payload["baselineWeight"] = sensor->baselineWeight;
    serializeJson(payload, payloadBuffer, sizeof(payloadBuffer));

    mqttClient.publish(topic.c_str(), payloadBuffer, true);
}

static void publishTopicTareRequest(const WeightSensor* sensor) {
    static bool tareRequested = false;
    if (tareRequested) return;

    const String topic = String(MQTT_DEVICE_ID) + "/" + sensor->id + "/tare/request";

    mqttClient.publish(topic.c_str(), "");
    tareRequested = true;
}

static void publishTopicTareSave(WeightSensor* sensor) {
    const String topic = String(MQTT_DEVICE_ID) + "/" + sensor->id + "/tare/save";

    JsonDocument payload;
    char payloadBuffer[32];

    payload["offset"] = getOffset(sensor);
    serializeJson(payload, payloadBuffer, sizeof(payloadBuffer));

    mqttClient.publish(topic.c_str(), payloadBuffer, true);
}

void mqttCallback(char* topic, byte* payload, unsigned int length) {
    char _topic[64];
    strncpy(_topic, topic, sizeof(_topic));
    _topic[sizeof(_topic) - 1] = '\0';

    DEBUG("Message from \"%s\" topic received.\r\n", _topic, payload);

    char* pipe = strchr(_topic, '/');
    if (pipe == NULL) return;

    *pipe = '\0';
    const char* deviceID = _topic;
    char* type = pipe + 1;
    if (strcmp(deviceID, MQTT_DEVICE_ID) != 0) return;

    JsonDocument json;
    if (deserializeJson(json, payload, length)) return;

    DEBUG("Message payload: \"%s\".\r\n", json.as<String>().c_str());

    if (strcmp(type, "stock") == 0) {
        const ButtonStatus status = buttonStatusToEnum(json["status"] | "");
        if (status == ButtonStatus::UNKNOWN_BUTTON_STATUS) return;

        lockButtons();
        stockBtn.status = status;
        digitalWrite(stockBtn.ledPin, status);
        unlockButtons();
        return;
    };

    if (strcmp(type, "security") == 0) {
        const ButtonStatus status = buttonStatusToEnum(json["status"] | "");
        if (status == ButtonStatus::UNKNOWN_BUTTON_STATUS) return;

        lockButtons();
        securityBtn.status = status;
        digitalWrite(securityBtn.ledPin, status);
        unlockButtons();
        return;
    };

    if (strcmp(type, "security/alarm") == 0) {
        const AlarmStatus status = alarmStatusToEnum(json["status"] | "");
        if (status == AlarmStatus::UNKNOWN_ALARM_STATUS) return;

        status == AlarmStatus::MUTE ? muteBuzzer(&buzzer) : unmuteBuzzer(&buzzer);
        return;
    };

    pipe = strchr(type, '/');
    if (pipe == NULL) return;

    *pipe = '\0';
    const char* shelfID = type;
    type = pipe + 1;

    if (strcmp(type, "tare") == 0) {
        if (strcmp(shelfID, weightSensor01.id.c_str()) == 0) {
            if (json["offset"].is<JsonVariant>()) {
                setOffset(&weightSensor01, json["offset"]);
            } else {
                tare(&weightSensor01);
                publishTopicTareSave(&weightSensor01);
            }
        };
    }
}

void xMQTTTask(void* parameters) {
    const uint32_t delay = pdMS_TO_TICKS(150);
    unsigned long lastPublishTime = 0;

    while (true) {
        bool clientConnected = mqttClient.connected();

        if (!clientConnected) {
            clientConnected = mqttClient.connect(
                MQTT_DEVICE_ID,
                MQTT_BROKER_USER,
                MQTT_BROKER_PASSWORD,
                topicHealth.c_str(),
                1,
                true,
                "{\"status\": \"offline\"}"
            );

            if (clientConnected) {
                const String topicStock = String(MQTT_DEVICE_ID) + "/stock";
                const String topicSecurity = String(MQTT_DEVICE_ID) + "/security";
                const String topicSecurityAlarm = String(MQTT_DEVICE_ID) + "/security/alarm";
                const String topicTare = String(MQTT_DEVICE_ID) + "/" + weightSensor01.id + "/tare";

                mqttClient.subscribe(topicStock.c_str());
                mqttClient.subscribe(topicSecurity.c_str());
                mqttClient.subscribe(topicSecurityAlarm.c_str());
                mqttClient.subscribe(topicTare.c_str());
            }
        }

        if (clientConnected) {
            mqttClient.loop();

            const unsigned long currentTime = millis();

            if (currentTime - lastPublishTime >= MQTT_PUBLISH_INTERVAL) {
                publishTopicHealth();
                publishTopicSystem();
                publishTopicAlarm();
                publishTopicStock(&weightSensor01);
                publishTopicSecurity(&weightSensor01);
                publishTopicTareRequest(&weightSensor01);
                lastPublishTime = currentTime;
            }
        }

        vTaskDelay(delay);
    }
}
