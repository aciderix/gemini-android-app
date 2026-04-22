<h1>
  <img src="docs/logo_gemini.png" alt="" height="32" align="top" />
  Code on Android
</h1>

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-2024.06-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Android API 26+](https://img.shields.io/badge/API-26%2B-3DDC84.svg?logo=android&logoColor=white)](https://developer.android.com/)
[![Status](https://img.shields.io/badge/status-usable-brightgreen.svg)](#)

> 🇬🇧 English · [🇫🇷 Version française](fr/README.md)

A **native Gemini coding client for Android** — not a wrapper around the
official Google app, not a thin webview. A real client that turns your
phone into a pocket coding workstation: the model reads and writes files,
runs shell commands (compiles, tests, starts servers), generates images,
and holds context across hours of conversation. Written in Kotlin +
Jetpack Compose.

<p align="center">
  <img src="docs/screenshot.jpg" alt="Chat in action: function calling + tool approval" width="360" />
</p>

## 🎯 What you can actually do with it

- **Ask the model to modify a project, not just describe one.** It opens
  files in your workspace (SAF or local folder), edits them literally,
  and shows you a diff. You approve once — or flip auto-approve and let
  it iterate on its own.
- **Run shell commands from the conversation.** The Termux bridge drops
  the model into your workspace directory: `python foo.py`, `npm test`,
  `cargo build`, `pip install …`, `curl`, `git status`. Backgrounded
  processes (servers, watchers) keep running when the model's turn ends.
- **Generate images inline.** Both **Imagen** (dedicated picker in
  Settings → Model) and **Gemini 2.5 Flash Image** ("Nano Banana",
  auto-enabled when you pick it in the top-bar dropdown) save their
  outputs to the chat bubble as thumbnails.
- **Send images for the model to analyse.** Tap the image icon, pick any
  photo from the gallery — it's sent as `inlineData` base64 in the next
  turn. The model can OCR, describe, or reason about the image.
- **Survive long sessions.** The app reports live token usage and
  auto-compresses the conversation into a fresh summary once the context
  window fills up, so you keep talking without 400 errors.
- **Autosave every turn.** Close the app, come back three days later,
  the conversation is exactly where you left it. Name and save snapshots
  from the drawer for archive.
- **Export anywhere.** Drawer → Export as Markdown opens the Android
  share sheet — send the full conversation (text, code blocks, tables,
  image references) to any app.

## ✨ Feature breakdown

- **Streaming chat** over `generativelanguage.googleapis.com`. API key
  encrypted locally in `EncryptedSharedPreferences`. No server in the
  middle.
- **Function calling** with 9 built-in tools:
  `read_file`, `write_file`, `edit_file`, `delete_file`,
  `list_directory`, `glob_files`, `grep`, `run_shell_command`
  (foreground or background), `generate_image` (Imagen). The model
  decides when to call them.
- **Safety on destructive tools**: every `write_file` / `edit_file` /
  `delete_file` / shell command shows an approval dialog with arguments
  and a diff (for edits) before it runs. One-tap "Auto-approve" toggle
  for trusted sessions.
- **Rich markdown rendering**: headings, numbered / bullet / task lists,
  inline + fenced code with a copy button, **bold**, *italic*, GFM
  tables, blockquotes, horizontal rules. Bare `https://…` URLs and
  `[label](url)` links are clickable and open in your browser.
- **Top-bar quick pickers**: tap the model name to switch models
  without opening Settings. Tap the workspace folder to "Open folder"
  (system Files app) or "Change folder" (SAF picker).
- **Multimodal input**: attach one or more images per turn; thumbnails
  render in the user bubble and persist across reloads. 15 MB cap per
  image.
- **Dynamic model list**: fetched live from `/v1beta/models`, no
  hardcoded catalog. Custom model IDs accepted in Settings.
- **Diff viewer** in the tool-result bubble for `edit_file`.
- **Two languages**: EN / FR interface, full parity.

## 🛠 Prerequisites

- **Android 8.0+** (API 26+).
- A **Gemini API key** (free tier works for chat; image generation
  requires billing on the associated Google Cloud project):
  <https://aistudio.google.com/app/apikey>.
- **Optional** — [Termux](https://f-droid.org/packages/com.termux/)
  from F-Droid or [the GitHub releases](https://github.com/termux/termux-app/releases)
  (⚠️ **not** the Play Store version, abandoned since 2020) if you want
  shell command execution.

To build from source:
- **Android SDK** (API 34+), **JDK 17** on `JAVA_HOME`.

## 🚀 Installation

### Option 1: pre-built APK

Download the latest APK from the
[Releases page](https://github.com/aciderix/gemini-android-app/releases).
Both debug-signed and release-signed APKs are published on each tag.

### Option 2: build from source

```bash
git clone https://github.com/aciderix/gemini-android-app
cd gemini-android-app
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 💻 First-run setup

1. **Settings → Account**: paste your Gemini API key. It's stored
   encrypted on device.
2. **Top-bar folder name → Change folder**: pick a workspace under
   `/storage/emulated/0/` (avoid `/Android/data/…`, unreachable to
   Termux). The file tools operate relative to this folder.
3. **Settings → Termux shell** (optional, one-time): follow the 3-step
   guide to allow `run_shell_command`. You need Termux installed and
   `termux-setup-storage` run once.
4. **Top-bar model name**: tap it to pick a model. Use
   `gemini-2.5-flash` for everyday coding, `gemini-2.5-pro` for harder
   reasoning, `gemini-2.5-flash-image-preview` if you want inline image
   generation (requires billing).

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

The model reports `usageMetadata.totalTokenCount` on every response.
Once `total / inputTokenLimit` crosses the threshold (default **70 %**),
the conversation is summarised into a fresh session in the background —
a non-blocking banner signals the operation.

Tune the threshold in **Settings → Auto-compression** (50 % → 95 %) or
disable it entirely.

### Image generation

- **Imagen** (`imagen-3.0-generate-002` by default, picker in
  Settings → Model) — called via the `generate_image` function tool
  when the model decides one is needed.
- **Gemini 2.5 Flash Image / Nano Banana** — pick it as the chat
  model, the app automatically enables `responseModalities: [TEXT, IMAGE]`
  on every request so the model returns images inline.
- Images are saved to `<filesDir>/attachments/` and render as
  thumbnails in the bubble; chat exports and reloads preserve them.

Both image paths require billing on the Google Cloud project linked to
your API key — the Gemini free tier has a quota of **0** for these
models.

### Background shell commands

`run_shell_command` accepts a `background: true` flag so the model can
start long-running processes (web servers, file watchers, training
loops) without Termux's 12-second IPC timeout killing them. Output
lands in `$HOME/.gemini-bg/run-<id>.log`, tailable on demand.

## 🧪 Gemini compatibility

Tested with:

- `gemini-2.5-pro` / `gemini-2.5-flash` / `gemini-2.5-flash-image-preview`
- `gemini-2.0-flash`
- `gemini-1.5-pro` / `gemini-1.5-flash`

**Gemma** models (`gemma-2-*`, `gemma-3-*`) appear in the picker but
don't support function calling — they'll work for pure chat but the
tool stack won't be available.

## 🤝 Contributing

Contributions welcome:

1. **Fork** the project.
2. Create a branch: `git checkout -b feature/my-feature`.
3. Commit with a clear message: `git commit -m "Add my feature"`.
4. Push: `git push origin feature/my-feature`.
5. Open a Pull Request.

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
