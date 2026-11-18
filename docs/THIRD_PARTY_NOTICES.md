# DrivePad — Third-Party Software Notices & Licensing Disclosures

This document summarizes the third-party libraries and external system interfaces used by **DrivePad (`com.drivepad.app`, v1.0.0)** and **DrivePad Companion (`v1.0.0`)**, audited directly from the resolved Gradle dependency graphs (`:app:releaseRuntimeClasspath` and `:companion:runtimeClasspath`).

---

## 1. DrivePad Application & Companion Core

- **DrivePad Android (`com.drivepad.app`)** and **DrivePad Companion (`:core`, `:linux`, `:windows`, `:companion`)** are project source modules governed by the repository license terms.
- Wire protocol **v1** (33-byte Little-Endian UDP) is self-contained without third-party protocol buffers or proprietary SDKs.

---

## 2. Android Application Runtime Dependencies (`:app:releaseRuntimeClasspath`)

All libraries packaged into the Android release APK are distributed under the **Apache License, Version 2.0 (`Apache-2.0`)**:

| Group / Artifact | Resolved Version | License | Role in APK |
| :--- | :--- | :--- | :--- |
| `org.jetbrains.kotlin:kotlin-stdlib` (+ `common`, `jdk7`, `jdk8`, `parcelize-runtime`) | `2.2.10` | Apache-2.0 | Kotlin standard library & runtime |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android`, `kotlinx-coroutines-core` | `1.10.2` | Apache-2.0 | Async telemetry loop & state flows |
| `org.jetbrains.kotlinx:kotlinx-serialization-core` | `1.6.3` | Apache-2.0 | Type-safe navigation route serialization |
| `org.jetbrains:annotations` | `23.0.0` | Apache-2.0 | Nullability & compiler annotations |
| `org.jspecify:jspecify` | `1.0.0` | Apache-2.0 | Standard Java nullability annotations |
| `androidx.compose:compose-bom` (`ui`, `foundation`, `animation`, `runtime`) | `2024.09.00` (`1.7.2`) | Apache-2.0 | Jetpack Compose UI toolkit & Canvas gauges |
| `androidx.compose.material3:material3` | `1.3.0` | Apache-2.0 | Material Design 3 components |
| `androidx.compose.material:material-icons-core`, `material-icons-extended`, `material-ripple` | `1.7.0` | Apache-2.0 | Material vector icons & touch ripples |
| `androidx.activity:activity-compose`, `activity`, `activity-ktx` | `1.10.1` | Apache-2.0 | Edge-to-edge activity host |
| `androidx.navigation:navigation-compose`, `navigation-runtime`, `navigation-common` | `2.8.9` | Apache-2.0 | Navigation Compose routing |
| `androidx.lifecycle:lifecycle-*` (`runtime`, `runtime-compose`, `viewmodel-compose`) | `2.8.7` | Apache-2.0 | Lifecycle-aware state collection |
| `androidx.datastore:datastore-preferences*` (+ `datastore-core-okio`) | `1.1.7` | Apache-2.0 | Local dashboard & IP preferences persistence |
| `androidx.core:core`, `core-ktx`, `core-viewtree` | `1.18.0` / `1.0.0` | Apache-2.0 | AndroidX Core compatibility & system UI |
| `androidx.collection:collection*`, `androidx.annotation:*`, `androidx.arch.core:*` | `1.4.4` / `1.8.1` / `2.2.0` | Apache-2.0 | AndroidX collections & support internals |
| `androidx.savedstate:*`, `androidx.profileinstaller`, `androidx.startup`, `androidx.tracing`, `androidx.emoji2`, `androidx.concurrent`, `androidx.graphics:graphics-path`, `androidx.interpolator`, `androidx.autofill`, `androidx.versionedparcelable` | `1.0.0`–`1.4.0` | Apache-2.0 | AndroidX Compose/Jetpack transitive runtime |
| `com.squareup.okio:okio` (`okio-jvm`) | `3.4.0` | Apache-2.0 | Transitive I/O backend for DataStore |
| `com.google.guava:listenablefuture` | `1.0` | Apache-2.0 | Transitive AndroidX concurrent future stub |

*(Debug/test dependencies such as `junit`, `robolectric`, `androidx.compose.ui:ui-tooling`, and `androidx.compose.ui:ui-test-manifest` are excluded from `release` builds.)*

---

## 3. PC Companion Runtime Dependencies (`:companion:runtimeClasspath`)

All libraries bundled inside `drivepad-companion-1.0.0-all.jar` and the Linux/Windows distribution archives:

| Artifact | Resolved Version | License | Notes & Obligations |
| :--- | :--- | :--- | :--- |
| `org.jetbrains.kotlin:kotlin-stdlib` | `2.2.10` | Apache-2.0 | Kotlin JVM runtime |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` (`core-jvm`) | `1.10.2` | Apache-2.0 | UDP listener & watchdog coroutines |
| `org.jetbrains:annotations` | `23.0.0` | Apache-2.0 | Compile/runtime annotations |
| `net.java.dev.jna:jna` | `5.15.0` | **Dual: `Apache-2.0` OR `LGPL-2.1-or-later`** | Used and redistributed by DrivePad under **`Apache-2.0`**. Bundles `jnidispatch` native JNI dispatcher libraries inside the JAR to call OS native interfaces (`/dev/uinput` libc `ioctl` on Linux; `ViGEmClient.dll` on Windows). Preserve JNA's `META-INF/AL2.0` and `LICENSE` notices in redistributions. |

*(No GPL-only userspace libraries are bundled or linked into the companion JAR.)*

---

## 4. External / Unbundled OS & Driver Interfaces

The following components are **NOT bundled** inside any DrivePad APK, JAR, `.tar.gz`, or `.zip` distribution:

### 4.1 Linux Kernel `uinput` UAPI (`GPL-2.0-only WITH Linux-syscall-note`)
- `:linux` interacts with the running host Linux kernel solely via standard character-device `open`/`write`/`ioctl`/`close` syscalls on `/dev/uinput` using Linux Input / `uinput` userspace ABI structures.
- No GPL kernel code or third-party userspace library is embedded in DrivePad.

### 4.2 Nefarius `ViGEmBus` (`v1.22.0`) & `ViGEmClient.dll` (`BSD-3-Clause`)
- **Lifecycle Notice:** Upstream Nefarius **`ViGEmBus`** was officially archived / End-of-Life in **November 2023**.
- **No Bundling:** DrivePad never bundles `ViGEmBus.exe`, `.sys`, or `ViGEmClient.dll`, and never silently installs kernel drivers.
- **License & Attribution (`BSD-3-Clause`):** Users who manually download and install official Microsoft-signed `ViGEmBus v1.22.0` and `ViGEmClient.dll` from Nefarius Software Solutions remain subject to the `BSD-3-Clause` copyright notice, condition list, and "AS IS" warranty disclaimer shipped with Nefarius's release.

---

## 5. Summary Compliance Obligations

1. Retain copyright notices and Apache-2.0 license text (`LICENSE` / `META-INF` notices preserved inside the Gradle-built JAR and APK).
2. Retain `THIRD_PARTY_NOTICES.md` alongside both `drivepad-companion-1.0.0-linux-x64.tar.gz` and `drivepad-companion-1.0.0-windows-x64.zip`.
3. Never bundle unverified or third-party `ViGEmBus` / `ViGEmClient.dll` binaries into release archives unless explicitly accompanied by full upstream `BSD-3-Clause` license texts and integrity verification.
