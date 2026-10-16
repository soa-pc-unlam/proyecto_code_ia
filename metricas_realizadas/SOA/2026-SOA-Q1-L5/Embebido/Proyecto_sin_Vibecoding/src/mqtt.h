#ifndef MQTT_H_INCLUDED
#define MQTT_H_INCLUDED

#include <PubSubClient.h>

#define WIFI_SSID     "Wokwi-GUEST"
#define WIFI_PASSWORD ""

#define MQTT_DEVICE_ID "corridor-01"

#define MQTT_BROKER_HOST "localhost"
#define MQTT_BROKER_PORT 1883

#define MQTT_BROKER_USER     "grupo-l5"
#define MQTT_BROKER_PASSWORD "secretl5"

#define MQTT_KEEPALIVE 15

#define MQTT_PUBLISH_INTERVAL 150

extern WiFiClient espClient;
extern PubSubClient mqttClient;

void mqttCallback(char* topic, byte* payload, unsigned int length);

void xMQTTTask(void* parameters);

#endif
