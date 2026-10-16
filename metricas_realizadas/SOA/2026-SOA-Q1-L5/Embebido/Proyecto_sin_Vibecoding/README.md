<h1 align="center">
    SOA Practical Work [2026]
</h1>

<p align="center">
    <strong>Practical work repository for the Advanced Operating Systems (SOA) subject</strong>
    <br>
    <strong>- <a href="https://www.unlam.edu.ar/">UNLaM</a> (National University of La Matanza) -</strong>
</p>

<p align="center">
    <a href="#summary">Summary</a> •
    <a href="#installation">Installation</a> •
    <br>
    <a href="#project-structure">Project structure</a> •
    <a href="#development-team">Development team</a> •
    <a href="#license">License</a> •
    <a href="#acknowledgments">Acknowledgments</a>
</p>

<p align="center">
    <img src="docs/statics/billboard.png" alt="Billboard" width="800">
</p>

## Summary

This repository contains our practical work for the Advanced Operating Systems (SOA) subject at the [National University of La Matanza (UNLaM)](https://www.unlam.edu.ar/). It consists of an embedded system based on an ESP32, which has two operating modes (activated by push buttons):

- **Virgin Embedded:** The system is idle, waiting for a mode to be activated.

- **Stock Mode:** Reports the current stock and alerts if the quantity on the shelves is below the established minimum. It uses an LCD screen to show the existing stock or the shortage, LED lights to visually indicate which shelf needs restocking, and weight sensors to calculate the stock on each shelf.

- **Security Mode:** Detects variations in the weight of the shelves. It employs an LCD screen to report on which shelf the alteration was detected, a buzzer as an audible alarm, LED lights to visually indicate the affected shelf, and weight sensors to register these variations. It has a higher priority than stock mode, so if both modes are activated at the same time, the system will execute security mode.

> [!TIP]
> Check out the [infrastructure documentation](docs/infrastructure.md) for more information about the MQTT topics and HTTP endpoints used in this project.

### Features

- Architectural design of a Mosquitto (MQTT broker), bridged to an Android app through Node-RED.
- Code conventions and standards
- Design and development of a finite state machine (FSM) to act according to the operating mode.
- Handling of sensors and actuators on an ESP32.
- Integration between an embedded system and a mobile application developed in Android.
- MQTT and HTTP testing.
- MQTT integration between the ESP32 and an Android application through Node-RED.
- [Real-time monitoring of stock and security alerts](#mqtt-integration) over MQTT, with remote control.

## Installation

1. Clone the repository.
1. Install [Visual Studio Code](https://code.visualstudio.com/), [Docker](https://www.docker.com/), and [Dev Containers](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers) (VSCode extension).
1. Open the repository folder in Visual Studio Code.
1. Reopen the project in a Dev Container, pressing `F1` and selecting `Dev Containers: Rebuild and Reopen in Container`.
1. Wait for the container to be built and started.
1. Get the IP of your local machine and replace the `MQTT_BROKER_HOST` macro inside `src/mqtt.h` file with it.
1. Execute `docker compose up --file infrastructure/compose.yaml --detach` to start the MQTT broker and Node-RED.
1. Press `F1` and select `Wokwi: Request a new License` option to get a free license for build the project.
1. When you have the license, press `F1` and select `PlatformIO: Build` to build the source code.
1. After the build is finished, press `F1` and select `Wokwi: Start Simulator` to run the project.
1. That's it! You should see the project running in the Wokwi simulator connected.

<details>
<summary>How can I turn on the debug mode?</summary>

1. Open `src/debuggers.h` file and change the value of `DEBUG_MODE` macro to `true`.
2. Press `F1` and select `PlatformIO: Build` to rebuild the project.
3. After the build is finished, press `F1` and select `Wokwi: Start Simulator` to run the project.
4. That's it! Now the project will print debug messages to the serial monitor.

</details>

<details>
<summary>How can I upload the project to a physical ESP32?</summary>

1. Make sure you are in the Dev Container.
2. Open `src/mqtt.h` file and replace the `WIFI_SSID` and `WIFI_PASSWORD` macros with the credentials of the Wi-Fi network you want to connect to.
3. Press `F1` and select `PlatformIO: Build` to rebuild the project.
4. Connect your ESP32 to your computer using a USB cable.
5. After the build is finished, press `F1` and select `PlatformIO: Upload` to upload the project to the ESP32.
6. Wait for the upload process to finish.
7. Once the upload is complete, the project should start running on the ESP32.

</details>

<details>
<summary>How can I connect the Android application with the ESP32?</summary>

1. Make sure you have the Android application installed on your mobile device.
2. Open the Android application and go to settings.
3. Enter the IP address of your local machine (the same one you used in the `MQTT_BROKER_HOST` macro inside `src/mqtt.h`) in the `MQTT broker host` field.
4. Press the "Connect" button to establish a connection with the MQTT broker.
5. Once connected, you should be able to receive real-time security and stock alerts through the Android application and control the system remotely.

</details>

> [!NOTE]
> When rebuilding the DevContainer, you might encounter an error such as `The container already exists or is in use`. Don't worry, it's a known bug, simply ignore it and rebuild it again.

> [!IMPORTANT]
> The first time you build the DevContainer, PlatformIO extension will request you to restart Visual Studio Code to finish the installation. Please do so, otherwise you won't be able to build the project.

## Project structure

```bash
SOA-Practical-Work-2026/
│
├── .devcontainer/          # DevContainer configuration files.
│
├── .github/
│   ├── workflows/          # GitHub Actions (CI/CD) workflows.
│   │
│   └── CODEOWNERS          # File to assign Pull Request and Issue reviewers.
│
├── docs/                   # Documentation files, such as diagrams, images, and other statics.
│
├── infrastructure/         # Infrastructure files related with the MQTT broker and Node-RED.
│   ├── mosquitto/          # Mosquitto persistence files, such as the configuration file.
│   ├── node-red/           # Node-RED persistence files, such as the flows and settings files.
│   │
│   ├── compose.yaml        # Docker Compose file to run the MQTT broker and Node-RED.
│   └── endpoints.http      # HTTP endpoints testing file for the REST Client extension.
│
├── scripts/
│   └── health-check.sh     # Script to check that the necessary tools are installed.
│
├── src/
│   ├── constants.h         # Definitions of global constants.
│   ├── constants.ino       # Implementation of global constants.
│   ├── debuggers.h         # Debugging macros.
│   ├── enums.h             # Global enums, and system status and events.
│   ├── enums.ino           # Implementation of utility functions to convert enums to strings and vice versa.
│   ├── event_captures.h    # Definitions of event captures for the FSM.
│   ├── event_captures.ino  # Implementation of event captures for the FSM.
│   ├── main.ino            # Entry point.
│   ├── mqtt.h              # Definitions of MQTT topics and payloads, and functions to publish messages to the MQTT broker.
│   ├── mqtt.ino            # Implementation of functions to publish messages to the MQTT broker.
│   ├── pins.h              # ESP32 pin's designations.
│   ├── structs.h           # Structs for global constants.
│   ├── sync.h              # Definitions of synchronization functions to handle semaphores and mutexes.
│   ├── sync.ino            # Implementation of synchronization functions.
│   ├── tasks.h             # Definitions of FreeRTOS tasks.
│   ├── tasks.ino           # Implementation of FreeRTOS tasks.
│   ├── user_functions.h    # Definitions of utility functions for sensors and actuators.
│   └── user_functions.ino  # Implementation of utility functions for sensors and actuators.
│
├── .clang-format           # Clang-format configuration (code formatter).
├── .editorconfig           # Configuration to standardize code style across different IDEs.
├── .gitattributes          # Configuration for Git attributes.
├── .gitignore              # List of all files and folders that Git should ignore.
├── .oxfmtrc.json           # Oxfmt configuration (code formatter).
├── cspell.json             # Code Spell Checker extension configuration.
├── diagram.json            # Circuit diagram of the project, exported from Wokwi.
├── lefthook.yaml           # Lefthook configuration (Git hooks manager).
├── LICENSE                 # Project license.
├── platformio.ini          # PlatformIO configuration file.
├── README.md               # This file.
├── wokwi.toml              # Wokwi configuration file.
└── zizmor.yml              # Zizmor configuration (linter for GitHub Actions workflows).
```

## Development team

- [Casas Lautaro](https://github.com/lautarocasas)
- [Farias Maira](https://github.com/maifarias)
- [Hoz Lucas](https://github.com/hozlucas28)
- [Massa Valentin](https://github.com/ValentinMassa)

## Additional material

- [Infrastructure documentation (diagram, MQTT topics and HTTP endpoints)](docs/infrastructure.md)
- [Repository with the source code of the Android application](https://github.com/maifarias/SOA-Stock-Security-App)

## License

This repository is under the [MIT license](LICENSE). For more information about what is permitted with the contents of this repository, visit [choosealicense.com](https://choosealicense.com/licenses/).

## Acknowledgments

We would like to thank the teachers from the [UNLaM](https://www.unlam.edu.ar/) Advanced Operating Systems (SOA) subject for their support and guidance.
