#pragma region config
#define LEFT_DISTANCE_SENSOR_TRIG_PIN 32
#define LEFT_DISTANCE_SENSOR_ECHO_PIN 33

#define RIGHT_DISTANCE_SENSOR_TRIG_PIN 25
#define RIGHT_DISTANCE_SENSOR_ECHO_PIN 26

#define L298N_LEFT_FORWARD_PIN 13
#define L298N_LEFT_BACKWARD_PIN 14
#define L298N_LEFT_SPEED_PIN 27

#define L298N_RIGHT_FORWARD_PIN 17
#define L298N_RIGHT_BACKWARD_PIN 2
#define L298N_RIGHT_SPEED_PIN 5

#define LIGHT_SENSOR_PIN 34
#define LED_PIN 4

#define SPEED_OF_SOUND 0.017                       // 0.034 cm/us / 2
#define MOTOR_SPEED 100                            // 50% PWM
#define MAX_TIMEOUT_FOR_OBSTACLE_DETECTION_US 5000 // 5ms = 85cm max
#define MAX_DISTANCE_FOR_OBSTACLE_DETECTION_CM 30
#define LIGHT_THRESHOLD 550

#define TASK_DELAY_MS 50

#define QUEUE_SIZE 20
#define TASK_STACK_SIZE 2048

#define WIFI_SSID "ssid"
#define WIFI_PASSWORD "pass"

// #define MQTT_BROKER "157.151.237.152"
// #define MQTT_PORT 1883
#define MQTT_BROKER "129.80.92.190"
#define MQTT_PORT 80
#define USE_MQTT_AUTH true
#define MQTT_USER "user"
#define MQTT_PASSWORD "pass"
#define TOPIC_STATE "robot/state"
#define TOPIC_COMMAND "robot/command"
#define TOPIC_SENSORS "robot/sensors"

#pragma endregion

#pragma region imports
#include <Arduino.h>
#include <PubSubClient.h>
#include <WiFi.h>
#pragma endregion

#pragma region types
#define STATE_COUNT 14
#define EVENT_COUNT 10

enum class State : uint8_t
{
  // Light off mode
  STILL = 0,
  FORWARD = 1,
  BACKWARD = 2,
  LEFT = 3,
  RIGHT = 4,
  AVOIDING_OBSTACLE_LEFT = 5,
  AVOIDING_OBSTACLE_RIGHT = 6,

  // Light on mode
  STILL_LIGHT_ON = 7,
  FORWARD_LIGHT_ON = 8,
  BACKWARD_LIGHT_ON = 9,
  LEFT_LIGHT_ON = 10,
  RIGHT_LIGHT_ON = 11,
  AVOIDING_OBSTACLE_LEFT_LIGHT_ON = 12,
  AVOIDING_OBSTACLE_RIGHT_LIGHT_ON = 13,
};

enum class Event : uint8_t
{
  OBSTACLE_LEFT = 0,
  OBSTACLE_RIGHT = 1,
  PATH_FREE = 2,
  MOVE_FORWARD = 3,
  MOVE_LEFT = 4,
  MOVE_RIGHT = 5,
  MOVE_BACKWARD = 6,
  STOP = 7,
  LOW_LIGHT = 8,
  HIGH_LIGHT = 9
};

using SensorFunction = Event (*)();
using Action = void (*)();

struct Transition
{
  Action action;
  State nextState;
};

struct MqttMessage
{
  char topic[50];
  char payload[20];
};

static const char *stateToReadable(State state)
{
  switch (state)
    {
    case State::STILL:
      return "STILL";
    case State::FORWARD:
      return "FORWARD";
    case State::BACKWARD:
      return "BACKWARD";
    case State::LEFT:
      return "LEFT";
    case State::RIGHT:
      return "RIGHT";
    case State::AVOIDING_OBSTACLE_LEFT:
      return "AVOIDING_OBSTACLE_LEFT";
    case State::AVOIDING_OBSTACLE_RIGHT:
      return "AVOIDING_OBSTACLE_RIGHT";
    case State::STILL_LIGHT_ON:
      return "STILL_LIGHT_ON";
    case State::FORWARD_LIGHT_ON:
      return "FORWARD_LIGHT_ON";
    case State::BACKWARD_LIGHT_ON:
      return "BACKWARD_LIGHT_ON";
    case State::LEFT_LIGHT_ON:
      return "LEFT_LIGHT_ON";
    case State::RIGHT_LIGHT_ON:
      return "RIGHT_LIGHT_ON";
    case State::AVOIDING_OBSTACLE_LEFT_LIGHT_ON:
      return "AVOIDING_OBSTACLE_LEFT_LIGHT_ON";
    case State::AVOIDING_OBSTACLE_RIGHT_LIGHT_ON:
      return "AVOIDING_OBSTACLE_RIGHT_LIGHT_ON";
    default:
      return "UNKNOWN_STATE";
    }
}

static const char *eventToReadable(Event event)
{
  switch (event)
    {
    case Event::OBSTACLE_LEFT:
      return "OBSTACLE_LEFT";
    case Event::OBSTACLE_RIGHT:
      return "OBSTACLE_RIGHT";
    case Event::PATH_FREE:
      return "PATH_FREE";
    case Event::MOVE_FORWARD:
      return "MOVE_FORWARD";
    case Event::MOVE_LEFT:
      return "MOVE_LEFT";
    case Event::MOVE_RIGHT:
      return "MOVE_RIGHT";
    case Event::MOVE_BACKWARD:
      return "MOVE_BACKWARD";
    case Event::STOP:
      return "STOP";
    case Event::LOW_LIGHT:
      return "LOW_LIGHT";
    case Event::HIGH_LIGHT:
      return "HIGH_LIGHT";
    default:
      return "UNKNOWN_EVENT";
    }
}
#pragma endregion

#pragma region variables
QueueHandle_t events_queue;
QueueHandle_t mqtt_messages_queue;
WiFiClient wifi_client;
PubSubClient mqtt_client(wifi_client);
State current_state = State::STILL;

int last_light_value = -1;
float last_left_distance = -1;
float last_right_distance = -1;
#pragma endregion

#pragma region helpers
void publishMqtt(const char *topic, const char *payload)
{
  MqttMessage msg;
  strncpy(msg.topic, topic, sizeof(msg.topic) - 1);
  msg.topic[sizeof(msg.topic) - 1] = '\0';
  strncpy(msg.payload, payload, sizeof(msg.payload) - 1);
  msg.payload[sizeof(msg.payload) - 1] = '\0';
  xQueueSend(mqtt_messages_queue, &msg, 0);
}
#pragma endregion

#pragma region sensors
float check_left_distance_sensor()
{
  digitalWrite(LEFT_DISTANCE_SENSOR_TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(LEFT_DISTANCE_SENSOR_TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(LEFT_DISTANCE_SENSOR_TRIG_PIN, LOW);
  float duration = pulseIn(LEFT_DISTANCE_SENSOR_ECHO_PIN, HIGH, MAX_TIMEOUT_FOR_OBSTACLE_DETECTION_US);
  float distance = duration * SPEED_OF_SOUND;
  // float distance = random(7, 85);

  last_left_distance = distance;
  return distance;
}

float check_right_distance_sensor()
{
  digitalWrite(RIGHT_DISTANCE_SENSOR_TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(RIGHT_DISTANCE_SENSOR_TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(RIGHT_DISTANCE_SENSOR_TRIG_PIN, LOW);
  float duration = pulseIn(RIGHT_DISTANCE_SENSOR_ECHO_PIN, HIGH, MAX_TIMEOUT_FOR_OBSTACLE_DETECTION_US);
  float distance = duration * SPEED_OF_SOUND;
  // float distance = random(7, 85);

  last_right_distance = distance;
  return distance;
}

Event check_obstacles()
{
  float left_distance = check_left_distance_sensor();
  float right_distance = check_right_distance_sensor();

  if (left_distance > 0 && left_distance < MAX_DISTANCE_FOR_OBSTACLE_DETECTION_CM)
    {
      return Event::OBSTACLE_LEFT;
    }
  else if (right_distance > 0 && right_distance < MAX_DISTANCE_FOR_OBSTACLE_DETECTION_CM)
    {
      return Event::OBSTACLE_RIGHT;
    }
  else
    {
      return Event::PATH_FREE;
    }
}

Event check_light()
{
  int light_value = analogRead(LIGHT_SENSOR_PIN);
  // int light_value = random(0, 5000);

  last_light_value = light_value;
  return (light_value > LIGHT_THRESHOLD) ? Event::LOW_LIGHT : Event::HIGH_LIGHT;
}
#pragma endregion

#pragma region actuators
void switchOnLED()
{
  digitalWrite(LED_PIN, HIGH);
}

void switchOffLED()
{
  digitalWrite(LED_PIN, LOW);
}

void stopMotors()
{
  digitalWrite(L298N_LEFT_FORWARD_PIN, LOW);
  digitalWrite(L298N_LEFT_BACKWARD_PIN, LOW);
  digitalWrite(L298N_RIGHT_FORWARD_PIN, LOW);
  digitalWrite(L298N_RIGHT_BACKWARD_PIN, LOW);
}

void moveForward()
{
  stopMotors();
  digitalWrite(L298N_LEFT_FORWARD_PIN, HIGH);
  digitalWrite(L298N_RIGHT_FORWARD_PIN, HIGH);
}

void moveBackward()
{
  stopMotors();
  digitalWrite(L298N_LEFT_BACKWARD_PIN, HIGH);
  digitalWrite(L298N_RIGHT_BACKWARD_PIN, HIGH);
}

void moveLeft()
{
  stopMotors();
  digitalWrite(L298N_LEFT_BACKWARD_PIN, HIGH);
  digitalWrite(L298N_RIGHT_FORWARD_PIN, HIGH);
}

void moveRight()
{
  stopMotors();
  digitalWrite(L298N_LEFT_FORWARD_PIN, HIGH);
  digitalWrite(L298N_RIGHT_BACKWARD_PIN, HIGH);
}
#pragma endregion

#pragma region state_machine
static const Transition sm[STATE_COUNT][EVENT_COUNT] = {
  /* STILL */
  { /* OBSTACLE_LEFT */ { NULL, State::STILL },
    /* OBSTACLE_RIGHT */ { NULL, State::STILL },
    /* PATH_FREE */ { NULL, State::STILL },
    /* MOVE_FORWARD */ { moveForward, State::FORWARD },
    /* MOVE_LEFT */ { moveLeft, State::LEFT },
    /* MOVE_RIGHT */ { moveRight, State::RIGHT },
    /* MOVE_BACKWARD */ { moveBackward, State::BACKWARD },
    /* STOP */ { NULL, State::STILL },
    /* LOW_LIGHT */ { switchOnLED, State::STILL_LIGHT_ON },
    /* HIGH_LIGHT */ { NULL, State::STILL } },
  /* FORWARD */
  { /* OBSTACLE_LEFT */ { moveRight, State::AVOIDING_OBSTACLE_LEFT },
    /* OBSTACLE_RIGHT */ { moveLeft, State::AVOIDING_OBSTACLE_RIGHT },
    /* PATH_FREE */ { NULL, State::FORWARD },
    /* MOVE_FORWARD */ { moveForward, State::FORWARD },
    /* MOVE_LEFT */ { moveLeft, State::LEFT },
    /* MOVE_RIGHT */ { moveRight, State::RIGHT },
    /* MOVE_BACKWARD */ { moveBackward, State::BACKWARD },
    /* STOP */ { stopMotors, State::STILL },
    /* LOW_LIGHT */ { switchOnLED, State::FORWARD_LIGHT_ON },
    /* HIGH_LIGHT */ { NULL, State::FORWARD } },
  /* BACKWARD */
  { /* OBSTACLE_LEFT */ { NULL, State::BACKWARD },
    /* OBSTACLE_RIGHT */ { NULL, State::BACKWARD },
    /* PATH_FREE */ { NULL, State::BACKWARD },
    /* MOVE_FORWARD */ { moveForward, State::FORWARD },
    /* MOVE_LEFT */ { moveLeft, State::LEFT },
    /* MOVE_RIGHT */ { moveRight, State::RIGHT },
    /* MOVE_BACKWARD */ { moveBackward, State::BACKWARD },
    /* STOP */ { stopMotors, State::STILL },
    /* LOW_LIGHT */ { switchOnLED, State::BACKWARD_LIGHT_ON },
    /* HIGH_LIGHT */ { NULL, State::BACKWARD } },
  /* LEFT */
  { /* OBSTACLE_LEFT */ { NULL, State::LEFT },
    /* OBSTACLE_RIGHT */ { NULL, State::LEFT },
    /* PATH_FREE */ { NULL, State::LEFT },
    /* MOVE_FORWARD */ { moveForward, State::FORWARD },
    /* MOVE_LEFT */ { moveLeft, State::LEFT },
    /* MOVE_RIGHT */ { moveRight, State::RIGHT },
    /* MOVE_BACKWARD */ { moveBackward, State::BACKWARD },
    /* STOP */ { stopMotors, State::STILL },
    /* LOW_LIGHT */ { switchOnLED, State::LEFT_LIGHT_ON },
    /* HIGH_LIGHT */ { NULL, State::LEFT } },
  /* RIGHT */
  { /* OBSTACLE_LEFT */ { NULL, State::RIGHT },
    /* OBSTACLE_RIGHT */ { NULL, State::RIGHT },
    /* PATH_FREE */ { NULL, State::RIGHT },
    /* MOVE_FORWARD */ { moveForward, State::FORWARD },
    /* MOVE_LEFT */ { moveLeft, State::LEFT },
    /* MOVE_RIGHT */ { moveRight, State::RIGHT },
    /* MOVE_BACKWARD */ { moveBackward, State::BACKWARD },
    /* STOP */ { stopMotors, State::STILL },
    /* LOW_LIGHT */ { switchOnLED, State::RIGHT_LIGHT_ON },
    /* HIGH_LIGHT */ { NULL, State::RIGHT } },
  /* AVOIDING_OBSTACLE_LEFT */
  { /* OBSTACLE_LEFT */ { NULL, State::AVOIDING_OBSTACLE_LEFT },
    /* OBSTACLE_RIGHT */ { NULL, State::AVOIDING_OBSTACLE_LEFT },
    /* PATH_FREE */ { moveForward, State::FORWARD },
    /* MOVE_FORWARD */ { NULL, State::AVOIDING_OBSTACLE_LEFT },
    /* MOVE_LEFT */ { NULL, State::AVOIDING_OBSTACLE_LEFT },
    /* MOVE_RIGHT */ { NULL, State::AVOIDING_OBSTACLE_LEFT },
    /* MOVE_BACKWARD */ { NULL, State::AVOIDING_OBSTACLE_LEFT },
    /* STOP */ { stopMotors, State::STILL },
    /* LOW_LIGHT */ { switchOnLED, State::AVOIDING_OBSTACLE_LEFT_LIGHT_ON },
    /* HIGH_LIGHT */ { NULL, State::AVOIDING_OBSTACLE_LEFT } },
  /* AVOIDING_OBSTACLE_RIGHT */
  { /* OBSTACLE_LEFT */ { NULL, State::AVOIDING_OBSTACLE_RIGHT },
    /* OBSTACLE_RIGHT */ { NULL, State::AVOIDING_OBSTACLE_RIGHT },
    /* PATH_FREE */ { moveForward, State::FORWARD },
    /* MOVE_FORWARD */ { NULL, State::AVOIDING_OBSTACLE_RIGHT },
    /* MOVE_LEFT */ { NULL, State::AVOIDING_OBSTACLE_RIGHT },
    /* MOVE_RIGHT */ { NULL, State::AVOIDING_OBSTACLE_RIGHT },
    /* MOVE_BACKWARD */ { NULL, State::AVOIDING_OBSTACLE_RIGHT },
    /* STOP */ { stopMotors, State::STILL },
    /* LOW_LIGHT */ { switchOnLED, State::AVOIDING_OBSTACLE_RIGHT_LIGHT_ON },
    /* HIGH_LIGHT */ { NULL, State::AVOIDING_OBSTACLE_RIGHT } },

  // Light on mode

  /* STILL_LIGHT_ON */
  { /* OBSTACLE_LEFT */ { NULL, State::STILL_LIGHT_ON },
    /* OBSTACLE_RIGHT */ { NULL, State::STILL_LIGHT_ON },
    /* PATH_FREE */ { NULL, State::STILL_LIGHT_ON },
    /* MOVE_FORWARD */ { moveForward, State::FORWARD_LIGHT_ON },
    /* MOVE_LEFT */ { moveLeft, State::LEFT_LIGHT_ON },
    /* MOVE_RIGHT */ { moveRight, State::RIGHT_LIGHT_ON },
    /* MOVE_BACKWARD */ { moveBackward, State::BACKWARD_LIGHT_ON },
    /* STOP */ { NULL, State::STILL_LIGHT_ON },
    /* LOW_LIGHT */ { NULL, State::STILL_LIGHT_ON },
    /* HIGH_LIGHT */ { switchOffLED, State::STILL } },
  /* FORWARD_LIGHT_ON */
  { /* OBSTACLE_LEFT */ { moveRight, State::AVOIDING_OBSTACLE_LEFT_LIGHT_ON },
    /* OBSTACLE_RIGHT */ { moveLeft, State::AVOIDING_OBSTACLE_RIGHT_LIGHT_ON },
    /* PATH_FREE */ { NULL, State::FORWARD_LIGHT_ON },
    /* MOVE_FORWARD */ { moveForward, State::FORWARD_LIGHT_ON },
    /* MOVE_LEFT */ { moveLeft, State::LEFT_LIGHT_ON },
    /* MOVE_RIGHT */ { moveRight, State::RIGHT_LIGHT_ON },
    /* MOVE_BACKWARD */ { moveBackward, State::BACKWARD_LIGHT_ON },
    /* STOP */ { stopMotors, State::STILL_LIGHT_ON },
    /* LOW_LIGHT */ { NULL, State::FORWARD_LIGHT_ON },
    /* HIGH_LIGHT */ { switchOffLED, State::FORWARD } },
  /* BACKWARD_LIGHT_ON */
  { /* OBSTACLE_LEFT */ { NULL, State::BACKWARD_LIGHT_ON },
    /* OBSTACLE_RIGHT */ { NULL, State::BACKWARD_LIGHT_ON },
    /* PATH_FREE */ { NULL, State::BACKWARD_LIGHT_ON },
    /* MOVE_FORWARD */ { moveForward, State::FORWARD_LIGHT_ON },
    /* MOVE_LEFT */ { moveLeft, State::LEFT_LIGHT_ON },
    /* MOVE_RIGHT */ { moveRight, State::RIGHT_LIGHT_ON },
    /* MOVE_BACKWARD */ { moveBackward, State::BACKWARD_LIGHT_ON },
    /* STOP */ { stopMotors, State::STILL_LIGHT_ON },
    /* LOW_LIGHT */ { NULL, State::BACKWARD_LIGHT_ON },
    /* HIGH_LIGHT */ { switchOffLED, State::BACKWARD } },
  /* LEFT_LIGHT_ON */
  { /* OBSTACLE_LEFT */ { NULL, State::LEFT_LIGHT_ON },
    /* OBSTACLE_RIGHT */ { NULL, State::LEFT_LIGHT_ON },
    /* PATH_FREE */ { NULL, State::LEFT_LIGHT_ON },
    /* MOVE_FORWARD */ { moveForward, State::FORWARD_LIGHT_ON },
    /* MOVE_LEFT */ { moveLeft, State::LEFT_LIGHT_ON },
    /* MOVE_RIGHT */ { moveRight, State::RIGHT_LIGHT_ON },
    /* MOVE_BACKWARD */ { moveBackward, State::BACKWARD_LIGHT_ON },
    /* STOP */ { stopMotors, State::STILL_LIGHT_ON },
    /* LOW_LIGHT */ { NULL, State::LEFT_LIGHT_ON },
    /* HIGH_LIGHT */ { switchOffLED, State::LEFT } },
  /* RIGHT_LIGHT_ON */
  { /* OBSTACLE_LEFT */ { NULL, State::RIGHT_LIGHT_ON },
    /* OBSTACLE_RIGHT */ { NULL, State::RIGHT_LIGHT_ON },
    /* PATH_FREE */ { NULL, State::RIGHT_LIGHT_ON },
    /* MOVE_FORWARD */ { moveForward, State::FORWARD_LIGHT_ON },
    /* MOVE_LEFT */ { moveLeft, State::LEFT_LIGHT_ON },
    /* MOVE_RIGHT */ { moveRight, State::RIGHT_LIGHT_ON },
    /* MOVE_BACKWARD */ { moveBackward, State::BACKWARD_LIGHT_ON },
    /* STOP */ { stopMotors, State::STILL_LIGHT_ON },
    /* LOW_LIGHT */ { NULL, State::RIGHT_LIGHT_ON },
    /* HIGH_LIGHT */ { switchOffLED, State::RIGHT } },
  /* AVOIDING_OBSTACLE_LEFT_LIGHT_ON */
  { /* OBSTACLE_LEFT */ { NULL, State::AVOIDING_OBSTACLE_LEFT_LIGHT_ON },
    /* OBSTACLE_RIGHT */ { NULL, State::AVOIDING_OBSTACLE_LEFT_LIGHT_ON },
    /* PATH_FREE */ { moveForward, State::FORWARD_LIGHT_ON },
    /* MOVE_FORWARD */ { NULL, State::AVOIDING_OBSTACLE_LEFT_LIGHT_ON },
    /* MOVE_LEFT */ { NULL, State::AVOIDING_OBSTACLE_LEFT_LIGHT_ON },
    /* MOVE_RIGHT */ { NULL, State::AVOIDING_OBSTACLE_LEFT_LIGHT_ON },
    /* MOVE_BACKWARD */ { NULL, State::AVOIDING_OBSTACLE_LEFT_LIGHT_ON },
    /* STOP */ { stopMotors, State::STILL_LIGHT_ON },
    /* LOW_LIGHT */ { NULL, State::AVOIDING_OBSTACLE_LEFT_LIGHT_ON },
    /* HIGH_LIGHT */ { switchOffLED, State::AVOIDING_OBSTACLE_LEFT } },
  /* AVOIDING_OBSTACLE_RIGHT_LIGHT_ON */
  { /* OBSTACLE_LEFT */ { NULL, State::AVOIDING_OBSTACLE_RIGHT_LIGHT_ON },
    /* OBSTACLE_RIGHT */ { NULL, State::AVOIDING_OBSTACLE_RIGHT_LIGHT_ON },
    /* PATH_FREE */ { moveForward, State::FORWARD_LIGHT_ON },
    /* MOVE_FORWARD */ { NULL, State::AVOIDING_OBSTACLE_RIGHT_LIGHT_ON },
    /* MOVE_LEFT */ { NULL, State::AVOIDING_OBSTACLE_RIGHT_LIGHT_ON },
    /* MOVE_RIGHT */ { NULL, State::AVOIDING_OBSTACLE_RIGHT_LIGHT_ON },
    /* MOVE_BACKWARD */ { NULL, State::AVOIDING_OBSTACLE_RIGHT_LIGHT_ON },
    /* STOP */ { stopMotors, State::STILL_LIGHT_ON },
    /* LOW_LIGHT */ { NULL, State::AVOIDING_OBSTACLE_RIGHT_LIGHT_ON },
    /* HIGH_LIGHT */ { switchOffLED, State::AVOIDING_OBSTACLE_RIGHT } }
};
#pragma endregion

#pragma region tasks
void mqtt_callback(char *topic, byte *payload, unsigned int length)
{
  Event event;
  if (length == 7 && memcmp(payload, "forward", 7) == 0)
    {
      event = Event::MOVE_FORWARD;
    }
  else if (length == 8 && memcmp(payload, "backward", 8) == 0)
    {
      event = Event::MOVE_BACKWARD;
    }
  else if (length == 4 && memcmp(payload, "left", 4) == 0)
    {
      event = Event::MOVE_LEFT;
    }
  else if (length == 5 && memcmp(payload, "right", 5) == 0)
    {
      event = Event::MOVE_RIGHT;
    }
  else if (length == 4 && memcmp(payload, "stop", 4) == 0)
    {
      event = Event::STOP;
    }
  else
    {
      return;
    }

  xQueueSend(events_queue, &event, portMAX_DELAY);
}

void task_check_light_sensor(void *_)
{
  while (true)
    {
      Event event = check_light();
      xQueueSend(events_queue, &event, portMAX_DELAY);
      vTaskDelay(pdMS_TO_TICKS(1000));
    }
}

void task_check_obstacle_sensors(void *_)
{
  while (true)
    {
      Event event = check_obstacles();
      xQueueSend(events_queue, &event, portMAX_DELAY);
      vTaskDelay(pdMS_TO_TICKS(50));
    }
}

void task_commands(void *_)
{
  while (true)
    {
      String s = Serial.readStringUntil('\n');

      if (s.equals("forward"))
        {
          Event event = Event::MOVE_FORWARD;
          xQueueSend(events_queue, &event, portMAX_DELAY);
        }
      else if (s.equals("backward"))
        {
          Event event = Event::MOVE_BACKWARD;
          xQueueSend(events_queue, &event, portMAX_DELAY);
        }
      else if (s.equals("left"))
        {
          Event event = Event::MOVE_LEFT;
          xQueueSend(events_queue, &event, portMAX_DELAY);
        }
      else if (s.equals("right"))
        {
          Event event = Event::MOVE_RIGHT;
          xQueueSend(events_queue, &event, portMAX_DELAY);
        }
      else if (s.equals("stop"))
        {
          Event event = Event::STOP;
          xQueueSend(events_queue, &event, portMAX_DELAY);
        }

      vTaskDelay(pdMS_TO_TICKS(TASK_DELAY_MS));
    }
}

void connect_wifi(void *_)
{
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  while (WiFi.status() != WL_CONNECTED)
    {
      vTaskDelay(pdMS_TO_TICKS(200));
      Serial.println("Connecting to WiFi...");
    }
  Serial.println("Connected to WiFi");
  vTaskDelete(NULL);
}

void connect_mqtt()
{
  mqtt_client.setServer(MQTT_BROKER, MQTT_PORT);

  mqtt_client.setCallback(mqtt_callback);

  while (!mqtt_client.connected())
    {
      Serial.println("Connecting to MQTT...");
      boolean connected;
      if (USE_MQTT_AUTH)
        {
          connected = mqtt_client.connect("esp32_client", MQTT_USER, MQTT_PASSWORD);
        }
      else
        {
          connected = mqtt_client.connect("esp32_client");
        }
      if (connected)
        {
          mqtt_client.subscribe(TOPIC_COMMAND);
          publishMqtt(TOPIC_STATE, stateToReadable(current_state));
        }
      else
        {
          Serial.print("Failed to connect to MQTT, rc=");
          Serial.print(mqtt_client.state());
          Serial.println(" Retrying...");
          vTaskDelay(pdMS_TO_TICKS(500));
        }
    }
}

void mqtt_task(void *_)
{
  while (true)
    {
      if (WiFi.status() != WL_CONNECTED)
        {
          vTaskDelay(pdMS_TO_TICKS(1000));
          continue;
        }

      if (!mqtt_client.connected())
        {
          connect_mqtt();
        }
      else
        {
          mqtt_client.loop();
          MqttMessage message;
          while (xQueueReceive(mqtt_messages_queue, &message, pdMS_TO_TICKS(0)) == pdTRUE)
            {
              mqtt_client.publish(message.topic, message.payload);
            }
        }

        vTaskDelay(pdMS_TO_TICKS(300));
    }
}

void task_sensor_publish(void *_)
{
  while (true)
    {
      char payload[32];
      snprintf(payload, sizeof(payload), "%.2f;%.2f;%d", last_left_distance, last_right_distance, last_light_value);
      publishMqtt(TOPIC_SENSORS, payload);
      vTaskDelay(pdMS_TO_TICKS(500));
    }
}
#pragma endregion

State handleEvent(Event event)
{
  Transition transition = sm[static_cast<uint8_t>(current_state)][static_cast<uint8_t>(event)];
  if (transition.action)
    {
      transition.action();
    }
  return transition.nextState;
}

void setup()
{
  Serial.begin(9600);
  pinMode(LEFT_DISTANCE_SENSOR_TRIG_PIN, OUTPUT);
  pinMode(LEFT_DISTANCE_SENSOR_ECHO_PIN, INPUT);
  pinMode(RIGHT_DISTANCE_SENSOR_TRIG_PIN, OUTPUT);
  pinMode(RIGHT_DISTANCE_SENSOR_ECHO_PIN, INPUT);
  pinMode(LIGHT_SENSOR_PIN, INPUT);
  pinMode(LED_PIN, OUTPUT);
  pinMode(L298N_LEFT_FORWARD_PIN, OUTPUT);
  pinMode(L298N_LEFT_BACKWARD_PIN, OUTPUT);
  pinMode(L298N_RIGHT_FORWARD_PIN, OUTPUT);
  pinMode(L298N_RIGHT_BACKWARD_PIN, OUTPUT);
  pinMode(L298N_LEFT_SPEED_PIN, OUTPUT);
  pinMode(L298N_RIGHT_SPEED_PIN, OUTPUT);

  analogWrite(L298N_LEFT_SPEED_PIN, MOTOR_SPEED);
  analogWrite(L298N_RIGHT_SPEED_PIN, MOTOR_SPEED);

  events_queue = xQueueCreate(QUEUE_SIZE, sizeof(Event));
  mqtt_messages_queue = xQueueCreate(QUEUE_SIZE, sizeof(MqttMessage));
  xTaskCreate(connect_wifi, "WiFi Task", TASK_STACK_SIZE * 10, NULL, 1, NULL);
  xTaskCreate(mqtt_task, "MQTT Task", TASK_STACK_SIZE * 10, NULL, 1, NULL);
  xTaskCreate(task_check_light_sensor, "Light Sensor Task", TASK_STACK_SIZE, NULL, 1, NULL);
  xTaskCreate(task_check_obstacle_sensors, "Obstacle Sensors Task", TASK_STACK_SIZE, NULL, 5, NULL);
  xTaskCreate(task_commands, "Commands Task", TASK_STACK_SIZE, NULL, 1, NULL);
  xTaskCreate(task_sensor_publish, "Sensor Publish Task", TASK_STACK_SIZE, NULL, 1, NULL);
}

void loop()
{
  Event event;
  if (xQueueReceive(events_queue, &event, pdMS_TO_TICKS(5000)) == pdTRUE)
    {
      State newState = handleEvent(event);
      if (newState != current_state)
        {
          Serial.printf("Transitioning from %s to %s on event %s\n",
                        stateToReadable(current_state),
                        stateToReadable(newState),
                        eventToReadable(event));

          publishMqtt(TOPIC_STATE, stateToReadable(newState));
        }
      current_state = newState;
    }
  else
    {
      Serial.println("No event received, staying in current state.");
    }
}
