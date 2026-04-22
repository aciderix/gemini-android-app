<h1>
  <img src="../docs/logo_gemini.png" alt="" height="32" align="top" />
  Gemini Android
</h1>

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](../LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-2024.06-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Android API 26+](https://img.shields.io/badge/API-26%2B-3DDC84.svg?logo=android&logoColor=white)](https://developer.android.com/)
[![Status](https://img.shields.io/badge/status-usable-brightgreen.svg)](#)

> 🇫🇷 Version française · [🇬🇧 English](../README.md)

Un client **Gemini natif pour Android** — chat streaming avec *function calling*,
outils fichiers intégrés (SAF), pont Termux pour les commandes shell, et
persistance locale chiffrée. Écrit en Kotlin + Jetpack Compose, sans dépendance
à une application officielle Google. L'objectif : reproduire l'expérience du
[Gemini CLI](https://github.com/google-gemini/gemini-cli) dans un APK autonome
qui tient dans la poche.

![Aperçu du chat](../docs/screenshots/chat.png)

## 📋 Table des matières

- [Fonctionnalités](#-fonctionnalités)
- [Prérequis](#-prérequis)
- [Installation](#-installation)
- [Utilisation](#-utilisation)
- [Architecture](#-architecture)
- [Configuration avancée](#-configuration-avancée)
- [Compatibilité Gemini](#-compatibilité-gemini)
- [Roadmap](#-roadmap)
- [Contribuer](#-contribuer)
- [Licence](#-licence)
- [Crédits](#-crédits)

## ✨ Fonctionnalités

- **Chat Gemini streaming** sur `generativelanguage.googleapis.com`, avec clé
  API stockée en `EncryptedSharedPreferences`.
- **Outils fichiers natifs** via SAF (`read_file`, `write_file`, `edit_file`,
  `delete_file`, `list_directory`, `glob_files`, `grep`) — le modèle décide
  seul quand les appeler.
- **Pont Termux** pour les commandes shell (`run_shell_command`) avec chemin
  workspace cohérent : `python foo.py` retrouve le fichier écrit juste avant
  par `write_file`.
- **Approbation des outils destructifs** — l'utilisateur valide chaque écriture
  / suppression / commande shell, ou active « Auto-approve » une bonne fois.
- **Autosave** de la conversation courante, restaurée au prochain lancement.
- **Auto-compression** à X % du context window (seuil réglable) — la session
  est résumée automatiquement avant de saturer.
- **Découverte dynamique des modèles** via `/v1beta/models` (pas de liste codée
  en dur).
- **Rendu Markdown** dans le chat : titres, listes, code (inline + blocs),
  liens, gras/italique, tableaux GFM, citations, listes à cocher, règles
  horizontales, pièces jointes images.
- **Diff viewer** intégré pour visualiser les résultats de `edit_file`.
- **Export Markdown** de la conversation vers n'importe quelle app (partage
  système).

## 🛠 Prérequis

Avant de commencer, assurez-vous d'avoir :

- **Android 8.0+** (API 26+) sur votre appareil.
- Une **clé API Gemini** (gratuite) :
  <https://aistudio.google.com/app/apikey>.
- **Optionnel** — [Termux](https://f-droid.org/packages/com.termux/) depuis
  F-Droid ou [les releases GitHub](https://github.com/termux/termux-app/releases)
  (⚠️ **pas** le Play Store, abandonné depuis 2020).

Pour compiler depuis les sources :

- **Android SDK** (API 34 minimum).
- **JDK 17** dans `JAVA_HOME`.

## 🚀 Installation

### Option 1 : APK pré-compilé

Télécharger le dernier APK debug depuis la [page Releases](https://github.com/aciderix/gemini-android-app/releases)
et l'installer. La signature est une clé debug — à remplacer par un keystore
de release avant distribution publique.

### Option 2 : Compiler depuis les sources

1. Cloner le dépôt :
   ```bash
   git clone https://github.com/aciderix/gemini-android-app
   cd gemini-android-app
   ```

2. Lancer le build Gradle :
   ```bash
   ./gradlew :app:assembleDebug
   ```

3. L'APK est produit dans :
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

4. L'installer sur l'appareil (USB ou `adb install`) :
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

## 💻 Utilisation

Au premier lancement :

1. **Settings → Account** : coller votre clé API Gemini.
2. **Settings → Workspace → Pick folder** : choisir un dossier sous
   `/storage/emulated/0/` (évitez `/Android/data/…`, inaccessible à Termux).
3. **Settings → Termux shell** (facultatif, 3 étapes) : activer le pont shell
   si vous voulez les commandes `run_shell_command`.

Puis commencer à discuter — le modèle utilisera les outils fichiers et shell
au besoin.

### Exemples

**Lire un fichier du workspace** :
> *"Ouvre `notes.md` et résume-le."*

**Écrire + exécuter un script Python** :
> *"Écris un script `hello.py` qui affiche 'Hello', puis exécute-le."*

L'app lancera `write_file` puis `run_shell_command` via Termux, avec
approbation avant chaque action destructive (désactivable).

**Attacher une image** : taper sur l'icône 🖼️ dans la barre de saisie et
choisir une image depuis le picker système. L'image est envoyée en `inlineData`
base64 dans la prochaine requête multimodale.

**Exporter la conversation** : **Menu burger → Export as Markdown** — ouvre
le sélecteur de partage système avec la conversation au format Markdown.

## 🧱 Architecture

Projet Gradle multi-modules :

| Module           | Rôle                                                                  |
|------------------|-----------------------------------------------------------------------|
| `:app`           | UI Compose, ViewModels, Activity                                      |
| `:core-bridge`   | Client REST Gemini, registry d'outils, Termux IPC, SAF, prefs         |
| `:domain`        | Types purs (`GeminiMessage`, `ToolSpec`, `GeminiEvent`…) — sans Android |
| `:ui-components` | Tokens de design (theme, couleurs) partagés                           |

Tout le réseau passe par `RestGeminiCore` qui émet des `GeminiEvent` consommés
par `ChatViewModel`. Pas de framework DI, pas de Room : `SharedPreferences` +
fichiers JSON sous `filesDir`.

## ⚙️ Configuration avancée

### Auto-compression

Le modèle renvoie `usageMetadata.totalTokenCount` à chaque réponse. Dès que
`total / inputTokenLimit` dépasse le seuil (défaut **70 %**), la conversation
est résumée dans une nouvelle session en arrière-plan — un bandeau
non-bloquant s'affiche pendant l'opération.

Régler le seuil dans **Settings → Auto-compression** (50 % → 95 %).

### Autosave

Activée par défaut. La session courante est persistée dans
`filesDir/chat-current.json` après chaque tour, et restaurée à l'ouverture
(tant qu'aucune autre conversation n'est chargée). Indépendant des chats
nommés, qu'on sauvegarde manuellement dans la liste.

Toggle dans **Settings → Autosave**.

### Pièces jointes images

Icône 🖼️ dans le composer — ouvre le picker photo système, puis l'image est
encodée en base64 et envoyée en `inlineData` dans le prochain tour utilisateur.
MIME détecté via `ContentResolver`. Limite de 15 MB par image.

## 🧪 Compatibilité Gemini

Testé avec :

- `gemini-2.5-pro` / `gemini-2.5-flash`
- `gemini-2.0-flash`
- `gemini-1.5-pro` / `gemini-1.5-flash`

Le sélecteur dans **Settings → Model** liste tous les modèles de votre compte
qui supportent `generateContent`.

## 🗺 Roadmap

Voir [`docs/ROADMAP.md`](../docs/ROADMAP.md) pour le détail.

Principaux chantiers ouverts :

- [ ] Keystore de release + CI signée.
- [ ] Tests unitaires sur le parser d'outils et sur `Workspace`.
- [ ] R8 / minification en release.
- [ ] Switcher rapide multi-conversation.

## 🤝 Contribuer

Les contributions sont les bienvenues :

1. **Forkez** le projet.
2. Créez une branche :
   ```bash
   git checkout -b feature/ma-feature
   ```
3. **Commitez** avec un message clair :
   ```bash
   git commit -m "Ajout de ma feature"
   ```
4. **Poussez** votre branche :
   ```bash
   git push origin feature/ma-feature
   ```
5. Ouvrez une **Pull Request**.

### Setup dev

- **Langage** : Kotlin 1.9.24, target JDK 17.
- **UI** : Compose BOM 2024.06.00 + Material 3.
- **Versions centralisées** dans `gradle/libs.versions.toml`.

Commandes utiles :
```bash
./gradlew :app:assembleDebug        # APK debug
./gradlew :app:lint                 # Android lint
./gradlew :core-bridge:test         # tests unitaires
```

## 📄 Licence

Distribué sous la licence **Apache 2.0**. Voir [`LICENSE`](../LICENSE) pour les
détails.

## 🙏 Crédits

- **Inspiration** : [Gemini CLI](https://github.com/google-gemini/gemini-cli) de Google.
- **Modèle** : [Gemini API](https://ai.google.dev/) de Google.
- **Pont shell** : [Termux](https://termux.dev/) — merveille open-source.
- **UI** : [Jetpack Compose](https://developer.android.com/jetpack/compose) + Material 3.

Lien du projet : <https://github.com/aciderix/gemini-android-app>
