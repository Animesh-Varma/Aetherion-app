# AETHERION
**Open-source, local-first mobile automation framework & hardware attestation host**
> [!NOTE]  
> **Provisional README (v0.0.1-poc)**
> 
> Aetherion is currently in an early **Proof of Concept (PoC)** alpha stage. This README is provisional for the initial release (`v0.0.1`) and documents the core device telemetry extraction, cryptographic signing pipeline, and live protocol logger.
>
> ** Current Status & Expectations:**  
> Due to rapid upstream Meta/Bloks client version drift, **live end-to-end credential login is currently blocked by server-side version checks**. However, this release fully validates and demonstrates:
> - Dynamic hardware profiling and secret extraction from the host Android runtime.
> - Keystore-backed cryptographic nonces and `#PWD_INSTAGRAM:4` encryption validated against Meta endpoints.
> - Full offline profile hydration and secrets inspection via the built-in **"Preview Demo"** mode.
> 
> And since these were its original goals to begin with, this warrants release `v0.0.1-poc`
>
> **Development Lifecycle:** Following the initial prototype phase, development will transition to a structured Gitflow model. Public client components reside in `Aetherion-app` and `Aetherion-SDK`, while payload inspection and dynamic schema tracking are isolated within private `Aetherion-Internal`.

[![Version](https://img.shields.io/badge/Version-v0.0.1--poc-blue?style=flat-square&logo=android)](#)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-green.svg?style=flat-square)](https://github.com/Animesh-Varma/Aetherion-app/blob/master/LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-purple.svg?style=flat-square&logo=kotlin)](#)
[![Jetpack Compose](https://img.shields.io/badge/UI-Material_3-4285F4?style=flat-square&logo=jetpackcompose)](#)

In Greek mythology, *Aether* is the pure, radiant upper air that the gods breathe—untouched by terrestrial haze.

Automating modern mobile platforms (specifically Instagram) has become an exercise in frustration. Developers are usually forced to choose between fragile, CPU-heavy browser automation (Selenium/Puppeteer), expensive residential proxy pools, or brittle private API wrappers that trigger automated checkpoint bans within minutes. 

Aetherion bypasses these hurdles through a native local-first architecture. Instead of spoofing static browser headers, Aetherion acts as an authentic on-device execution environment—extracting genuine hardware profiles, generating cryptographic attestation signatures via the Android Keystore, and establishing persistent, low-power push connections; just like the native Instagram client does. This (in theory) allows Aetherion to avoid common reasons of account flagging.  

---

<h3 align="center">Contents</h3>

<p align="center">
  <a href="#ecosystem-architecture">Architecture</a> •
  <a href="#features">Features</a> •
  <a href="#how-it-works">How It Works</a> •
  <a href="#known-issues">Known Issues</a>
  <br>
  <a href="#technical-stack">Tech Stack</a> •
  <a href="#build-instructions">Build</a> •
  <a href="#disclaimer">Disclaimer</a> •
  <a href="#contact">Contact</a>
</p>

---

## Ecosystem Architecture

Aetherion is architected across segregated repositories to maintain a strict clean-room legal boundary while ensuring modularity:

```
┌────────────────────────────────────────────────────────────────────────┐
│                          AETHERION ECOSYSTEM                           │
├───────────────────────────────────┬────────────────────────────────────┤
│           PUBLIC CLIENTS          │          PRIVATE WORKBENCH         │
├───────────────────────────────────┼────────────────────────────────────┤
│  Aetherion-app (This Repo)        │  Aetherion-Internal                │
│  - Kotlin / Jetpack Compose host  │  - Scripts for intercepting traffic│
│  - Android Keystore ECDSA nonces  │  - Automated LLM based resolver    │
│  - Dynamic hardware profiling     │  - Bloks schema reverse-engine     │
├───────────────────────────────────┴────────────────────────────────────┤
│  Aetherion-SDK                                                         │
│  - Typed Python client for high-level scripting & batch operations     │
│  - Direct REST & GraphQL command dispatch using app-derived secrets    │
├────────────────────────────────────────────────────────────────────────┤
│  Aetherion-Hub & Plugins                                               │
│  - Modular plugin registry (music scrobbling to Notes/Profile, etc.)   │
└────────────────────────────────────────────────────────────────────────┘
```

- **`Aetherion-app` (This Repository):** An Android companion and headless host. It runs on hardware, extracts real device identifiers without hardcoded constants, executes the multi-stage authentication handshake, and persists sessions.
- **`Aetherion-SDK`:** A clean python SDK that would run inside Aetherion-app allowing for smooth automation and plugin creation.
- **`Aetherion-Internal`:** An isolated sandbox used to intercept, report on, and reverse-engineer proprietary endpoints and Bloks protocol changes.
- **`Aetherion-Hub`:** A decentralized plugin registry for community modules.

---

## Features

- **Zero Hardcoded Constants:** Unlike traditional reverse-engineered wrappers that hardcode outdated device fingerprints, Aetherion dynamically samples the host Android runtime (OpenGL ES version, display metrics, build radio, SoC characteristics, and carrier capabilities).
- **Cryptographic Signature Engine:** Generates genuine client-side nonces using Android Keystore ECDSA keys and encrypts credentials matching Instagram's native `#PWD_INSTAGRAM:4` RSA-OAEP / AES-GCM standards.
- **Integrated Live Protocol Inspector:** An inline, real-time Material 3 network drawer directly below the login pipeline that captures and visualizes live requests, responses, HTTP status codes, and payload errors as they happen (do note that this is just specific to the current POC stage).

---

## How It Works

### 1. Dynamic Hardware Profiling
Instead of shipping a static `user-agent` string that gets flagged immediately, Aetherion reads properties directly from `android.os.Build` and hardware device nodes. It derives a mathematically consistent fingerprint including:
- Android Release & API SDK level
- Display resolution, DPI, and density buckets
- CPU architecture and ABI capabilities
- Family Device ID, Phone ID, and Advertising GUIDs

### 2. The 6-Stage Handshake Pipeline
When authenticating, Aetherion steps through Instagram's exact native initialization flow:
1. **Device Registration (`launcher/sync`):** Fetches server experiment flags and establishes the server clock sync offset.
2. **Cryptographic Attestation (`attestation/create_android_keystore`):** Signs a server nonce using an on-device hardware-backed key pair.
3. **Password Encryption (`accounts/login`):** Encrypts credentials with public key block `#PWD_INSTAGRAM:4` before dispatching.
4. **Session Verification (`accounts/current_user`):** Probes the resulting session cookies (`sessionid`, `csrftoken`, `ds_user_id`).
5. **Profile Hydration (`users/{id}/info`):** Retrieves the full user profile model.
6. **Media Feed Sync (`feed/user` & `highlights_tray`):** Pulls down initial timeline reels and stories.

### 3. Local-First Secret Persistence
Extracted tokens and cryptographic headers (`authorization`, `ig-u-ds-user-id`, `x-ig-app-id`, device secrets) are stored exclusively on-device in secure storage. These can be audited via the UI or passed directly to `Aetherion-SDK` for external scripting.

---

## Known Issues

As `v0.0.1-poc` is an early proof-of-concept release, several limitations apply:

- **Client Version / Bloks Schema Drift:** Meta frequently iterates on Bloks UI schemas. Even with recent version codes, certain endpoints may return "client out of date" until schemas are updated from internal captures.
- **2FA Challenge Gate:** Two-factor authentication (SMS/TOTP) challenge flows are currently not automated in `v0.0.1-poc`. Accounts tested must have 2FA temporarily disabled.
- **Packaging:** Currently distributed as debug source; pre-built signed APKs will be published starting with `v0.1.0`.

---

## Technical Stack

- **Platform:** Android (Min SDK 29+)
- **Language:** Kotlin 2.0+
- **UI Framework:** Jetpack Compose (Material 3)
- **Networking:** OkHttp 4, Retrofit, Custom Logging & Signing Interceptors

---

## Build Instructions

Ensure you have Android Studio Ladybug (or newer) and JDK 17+ configured.

```bash
# 1. Clone the repository
git clone https://github.com/Animesh-Varma/Aetherion-app.git
cd Aetherion-app

# 2. Compile and assemble the Debug APK
./gradlew assembleDebug

# 3. Install to a connected device or emulator via ADB or just send the APK over to the android and Install with pkg manager
adb Install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Disclaimer

Aetherion is an independent educational project developed for security research, protocol analysis, and local-first automation. It is not affiliated with, authorized, maintained, or endorsed by Meta Platforms, Inc. or Instagram. Use responsibly and in accordance with applicable platform policies and terms of service.

---

## Contact

**Note:** I am a high school student building this in my spare time. Reverse-engineering modern mobile networking, cryptographic keystores, and protocol layers is an ongoing learning journey for me. Advice, feedback, and pull requests are always welcome!

- Email: `animesh_varma@protonmail.com`
