# GARNET Android App

A native Android app shell wrapping the GARNET web chat
(https://garnet.institute-of-ai.org) in a proper WebView, with a real
app icon, splash screen, and — most importantly — correctly wired
microphone permissions so **Live Chat and voice messages work**, which
is the part that silently breaks in most simple WebView wrapper apps if
not handled carefully.

## Two ways to build this — pick one

### Option A: No install needed — build in the cloud with GitHub Actions
If you don't want to install Android Studio (it's a large download,
~1-1.5GB plus several GB more for the Android SDK), you can build a
real, installable APK entirely on GitHub's free servers instead:

1. Create a free account at github.com if you don't have one
2. Create a new repository (Settings can be Private) and upload this
   entire `GarnetApp` folder's contents to it (drag-and-drop upload
   works fine on github.com, or use GitHub Desktop)
3. GitHub will automatically detect `.github/workflows/build.yml` and
   start building — click the **"Actions"** tab at the top of your repo
   to watch it run (takes a few minutes)
4. Once it finishes (a green checkmark), click into that workflow run,
   scroll to **"Artifacts"**, and download **garnet-debug-apk** — this
   is a `.zip` containing `app-debug.apk`
5. Transfer that `.apk` file to your Android phone (email it to
   yourself, use a cloud drive, or a USB cable) and tap it on the phone
   to install — you'll need to allow "install from unknown sources"
   when prompted, since it's not from the Play Store yet
6. This gives you the app to test, but NOT a way to edit the code
   visually — for that, or to eventually publish to the Play Store,
   you'll still want Option B eventually

### Option B: Install Android Studio (recommended if you'll keep working on this)

Because it wraps the existing site rather than reimplementing it, every
feature already built into GARNET (Live Chat, ElevenLabs voices,
language detection, filler responses, everything) works automatically
with zero duplicated code to maintain.

## What's included
- `MainActivity.kt` — the WebView shell, with:
  - Proper mic permission handling for Live Chat (the #1 way these apps
    normally fail)
  - File upload support (attaching images/documents to a chat message)
  - Download handling (any download button on the site)
  - Offline detection with a retry button
  - A branded splash screen using the same logo as the web app
  - Back button navigates the page's own history first, only exits the
    app once there's nowhere left to go back to
- App icon generated at all required densities from the site's own
  `logo.png`
- Dark theme matching the web app's own background/accent colors

## How to build and run it

1. Install **Android Studio** (free): https://developer.android.com/studio
2. Open Android Studio → **Open** → select this `GarnetApp` folder
3. Let Android Studio sync Gradle (first sync may take a few minutes —
   it will download the Gradle wrapper automatically since only the
   config, not the jar itself, is included in this project)
4. Plug in an Android phone via USB with **USB debugging** enabled
   (Settings → About Phone → tap "Build number" 7 times → Developer
   Options → USB debugging), or use Android Studio's built-in emulator
5. Click the green **Run ▶** button, select your device, and it installs
   and launches

## Before publishing to the Play Store
- **Change the package name** (`org.instituteofai.garnet` in
  `app/build.gradle.kts`'s `namespace`/`applicationId` and the Kotlin
  package folder) to one unique to your Google Play developer account
- Generate a proper **signed release build** (Android Studio: Build →
  Generate Signed Bundle/APK) rather than the debug build Run ▶
  produces
- Google Play requires a **privacy policy URL** — you likely already
  have `terms.html` on the site; a privacy policy covering microphone
  use (for Live Chat) will be needed too

## If the mic doesn't work on a real device
Double-check the phone's own Android **Settings → Apps → GARNET →
Permissions → Microphone** is set to Allow — if it was denied once, the
in-app prompt won't ask again, and it has to be re-enabled from system
settings directly.
