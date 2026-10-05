# Binary Clock for Android

A native port of the binary clock artifact. One app gives you four clocks:

- **App** – full screen with seconds. Tap the clock to hide the settings and system bars.
- **Widget** – hours and minutes, for the home screen and the lock screen widget page.
- **Live wallpaper** – home screen, lock screen, or both. Ticks every second while the screen is on and does nothing while it's off. On the lock screen the clock sits below the system clock.
- **Screen saver** – full clock with seconds while charging; dark, dimmed, and drifting slightly to protect the screen.

Every place has its own settings (layout, colors, shapes, 12-hour time, bit order, and so on). Open the app and pick the place under **Editing settings for**: App, Home screen widget, Lock screen widget, Home screen wallpaper, Lock screen wallpaper, or Screen saver. The clock at the top previews the place you picked. **Copy settings from another place** starts one from another's look.

Examples: rows on the home widget and columns on the lock screen; amber shapes on the wallpaper and plain lamps on the screen saver.

- The widget is one widget, but it uses the lock screen settings whenever Android reports that it's sitting on the lock screen.
- The wallpaper uses the lock screen settings only when applied to the lock screen alone. If you apply it to home and lock together, both use the home wallpaper settings. To give them different looks, apply the wallpaper twice, once for each screen.
- Settings from the earlier version carry over to every place until you change them.

**Vertical columns** turns each row into a column (hour, min, sec side by side), with values rising upward. **Hours on bottom / right** reverses the row order. Flip bit order puts the 1s at the top instead, and Show values becomes a single shared scale on the left. For the widget in this mode, resize it taller than it is wide.

Requires Android 13 or newer. No third-party libraries.

## Build it

### Option A: GitHub (nothing to install)
1. Create a new GitHub repository and upload everything in this folder (including the hidden `.github` folder).
2. Open the repository's **Actions** tab. The "Build APK" workflow runs on every push; you can also start it by hand with **Run workflow**.
3. When it finishes (about 3–5 minutes), open the run and download **binary-clock-apk** at the bottom. It's a zip containing `app-debug.apk`.

### Option B: Android Studio
1. Open this folder in Android Studio and let Gradle sync.
2. Build → Build APK(s), or plug in the phone with USB debugging on and press Run.

## Install on the phone
1. Copy the APK to the phone (or download it there) and open it.
2. Allow your browser or Files app to install unknown apps when Android asks.
3. Play Protect may warn that it doesn't recognize the developer, since the app is unsigned by a store. Choose to install anyway.

Builds use a fixed signing key (`app/debug.keystore`), so new versions install over old ones and keep your settings.

## Set up each clock
- **Home screen widget:** open the app and tap "Add widget to home screen", or long-press the home screen → Widgets → Binary Clock. Resize it freely.
- **Lock screen:** Settings → Display & touch → Lock screen → turn on "Widgets on lock screen". Then swipe in from the right edge of the lock screen, long-press, and add Binary Clock.
- **Wallpaper:** open the app and tap "Set as live wallpaper", then choose home screen, lock screen, or both. For the lock screen, also check Wallpaper & style for the system clock options (Android won't let any app replace the system clock itself).
- **Screen saver:** open the app and tap "Open screen saver settings" (or Settings → Display & touch → Screen saver), pick Binary clock, and choose when it starts (while charging, docked, or both).

## How the widget stays in time
Widgets can't run animations, so the clock is drawn as an image once a minute, timed to the minute boundary. The alarm does not wake the phone: nothing runs while the screen is off, and the widget refreshes as soon as the phone is awake again. A 30-minute system update backs the alarm up, so a missed tick can never leave the widget stale for long. The `USE_EXACT_ALARM` permission makes that timing exact; it's granted automatically for sideloaded apps.

## What can't be replaced
The always-on display, the status bar clock and the system lock screen clock belong to Android and can't be swapped by an app. The widget, wallpaper and screen saver above are the surfaces apps are allowed to take over.

## Project layout
- `ClockRenderer.kt` – all drawing (lamps, shapes, glow, labels). Used by every surface.
- `ClockSettings.kt` – shared preferences.
- `BinaryClockView.kt` – live, ticking clock view.
- `ClockActivity.kt` – the app screen and settings.
- `BinaryClockWidget.kt` – widget rendering and the minute alarm.
- `BinaryClockWallpaper.kt` – the live wallpaper.
- `BinaryClockDream.kt` – the screen saver.
