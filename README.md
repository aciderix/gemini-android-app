<h1>
  <img src="docs/logo_gemini.png" alt="" height="32" align="top" />
  Gemini Android
</h1>

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-2024.06-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Android API 26+](https://img.shields.io/badge/API-26%2B-3DDC84.svg?logo=android&logoColor=white)](https://developer.android.com/)
[![Status](https://img.shields.io/badge/status-usable-brightgreen.svg)](#)

> 🇬🇧 English · [🇫🇷 Version française](fr/README.md)

A **native Gemini client for Android** — streaming chat with *function
calling*, built-in file tools (SAF), Termux bridge for shell commands, and
encrypted local persistence. Written in Kotlin + Jetpack Compose, with no
dependency on any official Google app. The goal: reproduce the
[Gemini CLI](https://github.com/google-gemini/gemini-cli) experience in a
standalone APK that fits in your pocket.

![Chat preview](docs/screenshots/chat.png)

## 📋 Table of contents

- [Features](#-features)
- [Prerequisites](#-prerequisites)
- [Installation](#-installation)
- [Usage](#-usage)
- [Architecture](#-architecture)
- [Advanced configuration](#-advanced-configuration)
- [Gemini compatibility](#-gemini-compatibility)
- [Roadmap](#-roadmap)
- [Contributing](#-contributing)
- [License](#-license)
- [Credits](#-credits)

## ✨ Features

- **Streaming Gemini chat** over `generativelanguage.googleapis.com`, with
  the API key stored in `EncryptedSharedPreferences`.
- **Native file tools** via SAF (`read_file`, `write_file`, `edit_file`,
  `delete_file`, `list_directory`, `glob_files`, `grep`) — the model
  decides on its own when to call them.
- **Termux bridge** for shell commands (`run_shell_command`) with a
  consistent workspace path: `python foo.py` finds the file `write_file`
  just wrote.
- **Approval for destructive tools** — you confirm every write / delete /
  shell command, or flip "Auto-approve" once.
- **Autosave** of the current conversation, restored on next launch.
- **Auto-compression** at X % of the context window (configurable
  threshold) — the session is summarised automatically before it saturates.
- **Dynamic model discovery** via `/v1beta/models` (no hard-coded list).
- **Markdown rendering** in chat: headings, lists, code (inline +
  fenced), links, bold/italic, GFM tables, blockquotes, task lists,
  horizontal rules, image attachments.
- **Diff viewer** for `edit_file` results.
- **Markdown export** of the conversation to any app (system share
  sheet).

## 🛠 Prerequisites

Before you start, make sure you have:

- **Android 8.0+** (API 26+) on your device.
- A **Gemini API key** (free): <https://aistudio.google.com/app/apikey>.
- **Optional** — [Termux](https://f-droid.org/packages/com.termux/) from
  F-Droid or [the GitHub releases](https://github.com/termux/termux-app/releases)
  (⚠️ **not** from the Play Store — abandoned since 2020).

To build from source:

- **Android SDK** (API 34 minimum).
- **JDK 17** on `JAVA_HOME`.

## 🚀 Installation

### Option 1: pre-built APK

Download the latest debug APK from the
[Releases page](https://github.com/aciderix/gemini-android-app/releases)
and install it. It's debug-signed — replace with a release keystore
before public distribution.

### Option 2: build from source

1. Clone the repo:
   ```bash
   git clone https://github.com/aciderix/gemini-android-app
   cd gemini-android-app
   ```

2. Run the Gradle build:
   ```bash
   ./gradlew :app:assembleDebug
   ```

3. The APK lands in:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

4. Install it on the device (USB or `adb install`):
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

## 💻 Usage

On first launch:

1. **Settings → Account**: paste your Gemini API key.
2. **Settings → Workspace → Pick folder**: choose a folder under
   `/storage/emulated/0/` (avoid `/Android/data/…`, unreachable by Termux).
3. **Settings → Termux shell** (optional, 3 steps): enable the shell
   bridge if you want `run_shell_command` to work.

Then start chatting — the model will use the file and shell tools as
needed.

### Examples

**Read a file from the workspace**:
> *"Open `notes.md` and summarise it."*

**Write + run a Python script**:
> *"Write a `hello.py` script that prints 'Hello', then run it."*

The app will invoke `write_file` then `run_shell_command` via Termux,
with approval before each destructive action (toggleable).

**Attach an image**: tap the 🖼️ icon in the composer and pick one from
the system picker. The image is sent as `inlineData` (base64) in the
next multimodal request.

**Export the conversation**: **burger menu → Export as Markdown** —
opens the system share sheet with the chat in Markdown format.

## 🧱 Architecture

Multi-module Gradle project:

| Module           | Role                                                                  |
|------------------|-----------------------------------------------------------------------|
| `:app`           | Compose UI, ViewModels, Activity                                      |
| `:core-bridge`   | REST Gemini client, tool registry, Termux IPC, SAF, prefs             |
| `:domain`        | Pure types (`GeminiMessage`, `ToolSpec`, `GeminiEvent`…) — no Android |
| `:ui-components` | Shared design tokens (theme, colours)                                 |

All network traffic flows through `RestGeminiCore`, which emits
`GeminiEvent`s consumed by `ChatViewModel`. No DI framework, no Room:
`SharedPreferences` + JSON files under `filesDir`.

## ⚙️ Advanced configuration

### Auto-compression

The model reports `usageMetadata.totalTokenCount` on every response. As
soon as `total / inputTokenLimit` crosses the threshold (default **70 %**),
the conversation is summarised into a fresh session in the background —
a non-blocking banner shows during the operation.

Tune the threshold in **Settings → Auto-compression** (50 % → 95 %).

### Autosave

On by default. The live session is persisted to
`filesDir/chat-current.json` after every turn, and restored on launch
(as long as no other chat is loaded). Independent of named chats, which
are saved manually from the list.

Toggle in **Settings → Autosave**.

### Image attachments

Via the 🖼️ icon in the composer — the image is encoded to base64 and
sent as `inlineData` on the next user turn. MIME detected via
`ContentResolver`. 15 MB cap per image.

## 🧪 Gemini compatibility

Tested with:

- `gemini-2.5-pro` / `gemini-2.5-flash`
- `gemini-2.0-flash`
- `gemini-1.5-pro` / `gemini-1.5-flash`

The **Settings → Model** picker lists every model on your account that
supports `generateContent`.

## 🗺 Roadmap

See [`docs/ROADMAP.md`](docs/ROADMAP.md) for details (in French).

Main open items:

- [ ] Release keystore + signed CI.
- [ ] Unit tests on the tool parser and `Workspace`.
- [ ] R8 / minification in release.
- [ ] Fast multi-chat switcher.

## 🤝 Contributing

Contributions are welcome:

1. **Fork** the project.
2. Create a branch:
   ```bash
   git checkout -b feature/my-feature
   ```
3. **Commit** with a clear message:
   ```bash
   git commit -m "Add my feature"
   ```
4. **Push** your branch:
   ```bash
   git push origin feature/my-feature
   ```
5. Open a **Pull Request**.

### Dev setup

- **Language**: Kotlin 1.9.24, target JDK 17.
- **UI**: Compose BOM 2024.06.00 + Material 3.
- **Versions centralised** in `gradle/libs.versions.toml`.

Useful commands:
```bash
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:lint                 # Android lint
./gradlew :core-bridge:test         # unit tests
```

## 📄 License

Distributed under the **Apache 2.0** license. See [`LICENSE`](LICENSE)
for details.

## 🙏 Credits

- **Inspiration**: [Gemini CLI](https://github.com/google-gemini/gemini-cli) by Google.
- **Model**: [Gemini API](https://ai.google.dev/) by Google.
- **Shell bridge**: [Termux](https://termux.dev/) — an open-source gem.
- **UI**: [Jetpack Compose](https://developer.android.com/jetpack/compose) + Material 3.

Project link: <https://github.com/aciderix/gemini-android-app>
