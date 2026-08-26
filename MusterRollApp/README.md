# Muster Roll — Android App (source project)

This is a ready-to-build Android Studio project that wraps your Attendance &
Salary Register web app in a simple Android WebView, so it runs as a normal
installed app with its own icon — fully offline, no internet needed once built.

I could not compile the actual .apk file myself: this sandbox has no internet
access and no Android SDK/Gradle installed, so there's no way to download the
build tools or run a real Android build here. This project folder is complete
and correct — you just need to open it in Android Studio (free) on your own
computer and click Build. It takes about 5–10 minutes the first time.

## How to build the APK

1. Install **Android Studio** (free): https://developer.android.com/studio
2. Open Android Studio → **Open** → select this `MusterRollApp` folder.
3. Let it sync Gradle automatically (first time may take a few minutes while
   it downloads the Android build tools — this needs internet on your
   computer, just this one time).
4. Once synced, go to **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
5. When it finishes, click the **"locate"** link in the notification, or find
   the file at:
   `app/build/outputs/apk/debug/app-debug.apk`
6. Copy that `app-debug.apk` to your Android phone (via USB, email, Google
   Drive, etc.) and tap it to install. You may need to allow
   "Install from unknown sources" the first time.

## What's inside

- `app/src/main/assets/attendance-app.html` — your full attendance & salary
  app (calendar, OT, PF/ESI/LWF deductions, salary slip — everything you
  built in chat).
- `MainActivity.java` — a minimal WebView wrapper that loads that HTML file
  and enables local storage so your data (employees, attendance, salary
  records) is saved on the device between app opens.
- Data is stored locally on the device only (via the browser's local
  storage inside the WebView) — nothing is sent anywhere.

## Updating the app later

If you want to change the app itself, just edit
`app/src/main/assets/attendance-app.html` (or ask me for an updated version
and drop it in, replacing the old one) and rebuild the APK the same way.

## Alternative: no-code option

If you'd rather not install Android Studio, you can use
**https://www.pwabuilder.com** — upload/host the HTML file, point PWABuilder
at it, and it can generate a signed Android package for you without writing
any code. Android Studio gives you more control (custom icon, offline
bundling of the exact file), which is why this project uses that approach.
