<h1>
  <img src="../docs/logo_gemini.png" alt="" height="32" align="top" />
  Code sur Android
</h1>

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](../LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-2024.06-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Android API 26+](https://img.shields.io/badge/API-26%2B-3DDC84.svg?logo=android&logoColor=white)](https://developer.android.com/)
[![Status](https://img.shields.io/badge/status-usable-brightgreen.svg)](#)

> 🇫🇷 Version française · [🇬🇧 English](../README.md)

Un **client de coding Gemini natif pour Android** — pas un wrapper
autour de l'app Google, pas un webview déguisé. Un vrai client qui
transforme votre téléphone en poste de travail de poche : le modèle lit
et écrit vos fichiers, exécute des commandes shell (compilation, tests,
serveurs), génère des images, et garde le contexte sur des heures de
conversation. Écrit en Kotlin + Jetpack Compose.

<p align="center">
  <img src="../docs/screenshot.jpg" alt="Chat en action : function calling + approbation d'outil" width="360" />
</p>

## 🎯 Ce que vous pouvez réellement faire avec

- **Demander au modèle de modifier un projet, pas juste de le
  décrire.** Il ouvre les fichiers de votre workspace (SAF ou dossier
  local), les édite littéralement, et affiche un diff. Vous approuvez
  une fois — ou activez l'auto-approve et le laissez itérer tout seul.
- **Exécuter des commandes shell depuis la conversation.** Le pont
  Termux place le modèle dans le dossier de workspace : `python foo.py`,
  `npm test`, `cargo build`, `pip install …`, `curl`, `git status`.
  Les processus en arrière-plan (serveurs, watchers) continuent de
  tourner quand le tour du modèle se termine.
- **Générer des images inline.** À la fois **Imagen** (picker dédié
  dans Settings → Model) et **Gemini 2.5 Flash Image** ("Nano Banana",
  activé automatiquement quand vous le sélectionnez dans le dropdown
  top-bar) sauvegardent leurs sorties en tant que vignettes dans la
  bulle de conversation.
- **Envoyer des images au modèle pour analyse.** Tapez sur l'icône
  image, choisissez une photo de la galerie — elle est envoyée en
  `inlineData` base64 au prochain tour. Le modèle peut faire de l'OCR,
  décrire, ou raisonner sur l'image.
- **Tenir sur des sessions longues.** L'app affiche l'usage tokens en
  direct et auto-compresse la conversation dans un nouveau résumé dès
  que la fenêtre de contexte se remplit, pour continuer à parler sans
  erreur 400.
- **Autosave à chaque tour.** Fermez l'app, revenez trois jours plus
  tard, la conversation est exactement où vous l'aviez laissée.
  Nommez et sauvegardez des snapshots depuis le drawer pour les
  archiver.
- **Exporter n'importe où.** Drawer → Export as Markdown ouvre le
  sélecteur de partage Android — envoyez la conversation complète
  (texte, blocs de code, tableaux, références d'images) vers n'importe
  quelle app.

## ✨ Fonctionnalités en détail

- **Chat streaming** sur `generativelanguage.googleapis.com`. Clé API
  chiffrée localement dans `EncryptedSharedPreferences`. Pas de serveur
  intermédiaire.
- **Function calling** avec 9 outils intégrés :
  `read_file`, `write_file`, `edit_file`, `delete_file`,
  `list_directory`, `glob_files`, `grep`, `run_shell_command`
  (premier plan ou arrière-plan), `generate_image` (Imagen). Le modèle
  décide quand les appeler.
- **Sécurité sur les outils destructifs** : chaque `write_file` /
  `edit_file` / `delete_file` / commande shell affiche un dialog
  d'approbation avec les arguments et un diff (pour les édits) avant
  de s'exécuter. Toggle « Auto-approve » en un tap pour les sessions
  de confiance.
- **Rendu Markdown riche** : titres, listes numérotées / à puces / à
  cocher, code inline et blocs avec bouton copier, **gras**, *italique*,
  tableaux GFM, citations, règles horizontales. Les URLs `https://…`
  nues et les liens `[label](url)` sont cliquables et ouvrent le
  navigateur.
- **Pickers rapides en top bar** : tapez sur le nom du modèle pour en
  changer sans ouvrir Settings. Tapez sur le nom du dossier workspace
  pour « Open folder » (app Fichiers système) ou « Change folder »
  (picker SAF).
- **Entrée multimodale** : attachez une ou plusieurs images par tour,
  les vignettes apparaissent dans la bulle utilisateur et persistent à
  travers les reloads. Limite de 15 MB par image.
- **Liste de modèles dynamique** : récupérée en live depuis
  `/v1beta/models`, pas de catalogue figé. IDs de modèles personnalisés
  acceptés dans Settings.
- **Diff viewer** dans la bulle de résultat d'outil pour `edit_file`.
- **Bilingue** : interface EN / FR, parité complète.

## 🛠 Prérequis

- **Android 8.0+** (API 26+).
- Une **clé API Gemini** (le free tier fonctionne pour le chat ; la
  génération d'image nécessite l'activation du billing sur le projet
  Google Cloud associé) : <https://aistudio.google.com/app/apikey>.
- **Optionnel** — [Termux](https://f-droid.org/packages/com.termux/)
  depuis F-Droid ou [les releases GitHub](https://github.com/termux/termux-app/releases)
  (⚠️ **pas** la version Play Store, abandonnée depuis 2020) si vous
  voulez l'exécution de commandes shell.

Pour compiler depuis les sources :
- **Android SDK** (API 34+), **JDK 17** dans `JAVA_HOME`.

## 🚀 Installation

### Option 1 : APK pré-compilé

Télécharger la dernière APK depuis la
[page Releases](https://github.com/aciderix/gemini-android-app/releases).
APKs debug-signé et release-signé publiés à chaque tag.

### Option 2 : compiler depuis les sources

```bash
git clone https://github.com/aciderix/gemini-android-app
cd gemini-android-app
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 💻 Configuration initiale

1. **Settings → Account** : coller votre clé API Gemini. Stockée
   chiffrée sur l'appareil.
2. **Nom du dossier en top-bar → Change folder** : choisir un
   workspace sous `/storage/emulated/0/` (évitez `/Android/data/…`,
   inaccessible à Termux). Les outils fichiers opèrent relativement à
   ce dossier.
3. **Settings → Termux shell** (optionnel, une fois) : suivre le guide
   en 3 étapes pour autoriser `run_shell_command`. Il faut Termux
   installé et `termux-setup-storage` lancé une fois.
4. **Nom du modèle en top-bar** : tapez pour en choisir un.
   `gemini-2.5-flash` pour le coding quotidien, `gemini-2.5-pro` pour
   le raisonnement plus lourd, `gemini-2.5-flash-image-preview` pour la
   génération d'image inline (nécessite billing).

## 🧱 Architecture

Projet Gradle multi-modules :

| Module           | Rôle                                                                  |
|------------------|-----------------------------------------------------------------------|
| `:app`           | UI Compose, ViewModels, Activity                                      |
| `:core-bridge`   | Client REST Gemini, registry d'outils, Termux IPC, SAF, prefs         |
| `:domain`        | Types purs (`GeminiMessage`, `ToolSpec`, `GeminiEvent`…) — sans Android |
| `:ui-components` | Tokens de design (theme, couleurs) partagés                           |

Tout le réseau passe par `RestGeminiCore` qui émet des `GeminiEvent`
consommés par `ChatViewModel`. Pas de framework DI, pas de Room :
`SharedPreferences` + fichiers JSON sous `filesDir`.

## ⚙️ Configuration avancée

### Auto-compression

Le modèle renvoie `usageMetadata.totalTokenCount` à chaque réponse. Dès
que `total / inputTokenLimit` dépasse le seuil (défaut **70 %**), la
conversation est résumée dans une nouvelle session en arrière-plan —
un bandeau non-bloquant signale l'opération.

Réglage du seuil dans **Settings → Auto-compression** (50 % → 95 %) ou
désactivation totale.

### Génération d'image

- **Imagen** (`imagen-3.0-generate-002` par défaut, picker dans
  Settings → Model) — appelée via l'outil function `generate_image`
  quand le modèle décide qu'il en faut une.
- **Gemini 2.5 Flash Image / Nano Banana** — choisissez-le comme
  modèle de chat, l'app active automatiquement
  `responseModalities: [TEXT, IMAGE]` sur chaque requête pour que le
  modèle renvoie des images inline.
- Les images sont sauvegardées dans `<filesDir>/attachments/` et
  apparaissent en vignettes dans la bulle ; les exports de chat et
  reloads les préservent.

Les deux chemins image nécessitent le billing activé sur le projet
Google Cloud lié à votre clé API — le free tier Gemini a un quota de
**0** pour ces modèles.

### Commandes shell en arrière-plan

`run_shell_command` accepte un flag `background: true` pour que le
modèle puisse lancer des processus de longue durée (serveurs web, file
watchers, boucles d'entraînement) sans que le timeout IPC Termux de
12 secondes ne les tue. La sortie atterrit dans
`$HOME/.gemini-bg/run-<id>.log`, consultable à la demande.

## 🧪 Compatibilité Gemini

Testé avec :

- `gemini-2.5-pro` / `gemini-2.5-flash` / `gemini-2.5-flash-image-preview`
- `gemini-2.0-flash`
- `gemini-1.5-pro` / `gemini-1.5-flash`

Les modèles **Gemma** (`gemma-2-*`, `gemma-3-*`) apparaissent dans le
picker mais ne supportent pas le function calling — ils fonctionnent
pour du chat pur mais la stack d'outils sera indisponible.

## 🤝 Contribuer

Les contributions sont les bienvenues :

1. **Forkez** le projet.
2. Créez une branche : `git checkout -b feature/ma-feature`.
3. Commitez avec un message clair : `git commit -m "Ajout de ma feature"`.
4. Poussez : `git push origin feature/ma-feature`.
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

Distribué sous la licence **Apache 2.0**. Voir [`LICENSE`](../LICENSE)
pour les détails.

## 🙏 Crédits

- **Inspiration** : [Gemini CLI](https://github.com/google-gemini/gemini-cli) de Google.
- **Modèle** : [Gemini API](https://ai.google.dev/) de Google.
- **Pont shell** : [Termux](https://termux.dev/) — merveille open-source.
- **UI** : [Jetpack Compose](https://developer.android.com/jetpack/compose) + Material 3.

Lien du projet : <https://github.com/aciderix/gemini-android-app>
