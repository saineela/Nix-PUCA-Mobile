 <p align="center">
  <a href="https://github.com/saineela/puca">
    <img src="https://res.cloudinary.com/dh5uxc6ql/image/upload/v1790917615/93d18a47-5f3a-46e1-ad71-705b2680442f_anp8f1.png" alt="NIX PUCA" width="360">
  </a>
</p>

<h1 align="center">NIX Home — Android App</h1>

<p align="center">
  A native Android companion for NIX PUCA, built with Kotlin and Jetpack Compose.
</p>

<p align="center">
  <a href="https://github.com/saineela/puca">
    <img alt="Part of NIX PUCA" src="https://img.shields.io/badge/part%20of-NIX%20PUCA-2f6f4e?style=for-the-badge">
  </a>
  <img alt="Platform: Android" src="https://img.shields.io/badge/platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white">
  <img alt="License: Apache 2.0" src="https://img.shields.io/badge/license-Apache%202.0-blue?style=for-the-badge">
</p>

---

NIX Home is the native Android application for interacting with your NIX console from your phone. It provides a mobile interface for conversations, connected devices, music, schedules, skills, and hardware configuration.

The app is built with **Kotlin and Jetpack Compose**, with encrypted local credential storage and Bluetooth Low Energy (BLE) support for device discovery and Wi-Fi provisioning.

## Features

- **Home & Chat** — Interact with your configured NIX console through the mobile interface.
- **Devices** — View and interact with supported devices through the console.
- **Music** — Access the music interface and playback controls exposed by the connected console.
- **Schedule** — View schedule information provided by the console.
- **Skills** — Browse the skill information exposed by the console.
- **Hardware** — Access supported hardware controls and information.
- **Bluetooth pairing** — Discover compatible nearby devices and provision Wi-Fi over BLE.
- **Setup** — Configure the connection to your NIX console.
- **Secure credential storage** — Store supported credentials using Android's Keystore-backed encrypted preferences.

Features that require a connected console or compatible hardware depend on those services being configured and available.

## Requirements

- Android 8.0 (API 26) or later
- Android Studio
- JDK 17
- Android SDK Platform 35 for the current build configuration
- A running NIX console for server-backed functionality

## Getting started

### Build from source

Clone the repository:

```bash
git clone https://github.com/saineela/Nix-PUCA-Mobile.git
cd Nix-PUCA-Mobile
```

Open the project in Android Studio and allow Gradle to synchronize.

Build the debug APK:

**Linux / macOS**

```bash
./gradlew assembleDebug
```

**Windows**

```powershell
.\gradlew.bat assembleDebug
```

The APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Install on a device

Connect an Android device with USB debugging enabled, or start an emulator.

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Launch NIX Home and configure your NIX console connection in the app's setup or settings screen.

## Connecting to NIX

NIX Home is a **client application**, not a standalone AI backend. It communicates with a configured NIX console for server-backed features.

Before connecting:

1. Ensure your console is running and reachable from your phone.
2. Configure the console's address in the app.
3. Supply any required credentials through the app's supported configuration flow.
4. Grant permissions when prompted for features such as Bluetooth discovery or calendar access.
5. Verify that the app can reach the console before troubleshooting individual features.

For console installation and server configuration, see the [NIX PUCA repository](https://github.com/saineela/puca).

## Security & privacy

- Credentials are stored using Android's encrypted credential-storage facilities backed by the Android Keystore.
- Bluetooth and calendar permissions are requested for corresponding application functionality.
- Keep service tokens and private configuration out of source control.
- The app supports cleartext traffic for local console connections in its current configuration. Use HTTPS when connecting across untrusted networks.
- Network security also depends on how the NIX console is deployed and exposed.

## Tech stack

| Technology | Purpose |
|---|---|
| Kotlin | Application language |
| Jetpack Compose | Native UI |
| Material 3 | UI components |
| AndroidX Lifecycle | Lifecycle-aware components |
| Kotlin Coroutines | Asynchronous operations |
| Android Keystore | Hardware-backed or system-managed key protection |
| EncryptedSharedPreferences | Encrypted preference storage |

## Project structure

```text
app/
└── src/
    └── main/
        ├── java/com/nixpuca/home/
        │   ├── MainActivity.kt
        │   ├── NixViewModel.kt
        │   ├── data/
        │   └── ui/
        └── AndroidManifest.xml

gradlew
gradlew.bat
build.gradle.kts
settings.gradle.kts
```

The exact package organization may evolve as development continues.

## Project status

NIX Home is under active development. Availability of individual screens and integrations depends on the current application implementation and the services exposed by the connected console.

## License

This project is licensed under the Apache License 2.0. See the repository's `LICENSE` file for the complete terms.

---

<p align="center">
  Built as part of <a href="https://github.com/saineela/puca"><strong>NIX PUCA</strong></a>.
</p>
