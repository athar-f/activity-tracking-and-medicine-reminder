# Pulse Tracker — Activity Tracking & Medicine Reminders

An Android app that uses the phone's sensors to detect activity (stationary / walking / running), records GPS routes, monitors ambient light and atmospheric pressure, and delivers offline medicine reminders.

**Native Android (Kotlin)** owns login, sensors, the background service, storage and reminders. **React Native (TypeScript)** renders all of the app's UI.

| | |
|---|---|
| React Native | 0.86.3 (New Architecture, Hermes) |
| Native | Kotlin 2.1, minSdk 26, targetSdk 36 |
| Storage | SQLite (native `SQLiteOpenHelper`) |
| UI | Material 3 colour system, light and dark themes |

---

## Features

| Requirement | Where |
|---|---|
| Activity detection from accelerometer + gyroscope, with current activity and its duration in real time | `tracking/ActivityClassifier.kt`, Dashboard |
| Medicine reminders with an **offline notification 15 min before** each dose, working when the app is closed or killed and after a reboot | `reminders/*`, Medicines screen |
| GPS tracking: route on a map and total distance per session | `tracking/LocationTracker.kt`, Map screen |
| Dashboard: activity + duration, distance, ambient light, pressure (when the device has a barometer) | Dashboard screen |
| Local storage of activity, location, light and pressure data, plus a history screen | `data/*`, History and Session Detail screens |
| Native **foreground service** that keeps tracking while the app is minimised | `tracking/TrackingService.kt` |
| Native **login** (Kotlin, Material Components) integrated with RN | `auth/LoginActivity.kt`, `bridge/AuthModule.kt` |
| **Bonus:** real-time charts (motion, light, pressure) | `src/components/LineChart.tsx` |
| **Bonus:** CSV export of a session through the share sheet | `export/CsvExporter.kt` |
| Extra: map-free route sketch (works offline / without a Maps key), estimated altitude from the barometer, step cadence, pace, "test reminder" button, dark mode | — |

---

## Architecture

```
┌──────────────────────────── React Native (TypeScript) ────────────────────────────┐
│ screens/     Dashboard · Map · History · SessionDetail · Medicines                │
│ state/       TrackingContext – one subscription to native events, shared by all   │
│ components/  StatCard · LineChart · RouteMap · RouteSketch · ActivityBreakdown …  │
│ native/      modules.ts (typed wrappers) · types.ts (bridge contract)             │
└───────────────▲───────────────────────────────────────────────┬───────────────────┘
   events: TrackingUpdate (1 Hz), LocationUpdate          Promise-based calls
┌───────────────┴───────────────────────────────────────────────▼───────────────────┐
│                               Native Android (Kotlin)                             │
│ auth/       LoginActivity (launcher) · SessionManager                             │
│ bridge/     TrackingModule · ReminderModule · AuthModule · AppPackage · Mappers   │
│ tracking/   TrackingService (foreground, type=location)                           │
│               ├─ SensorHub          accelerometer, gyroscope, light, pressure     │
│               ├─ ActivityClassifier windowed features → activity + hysteresis     │
│               ├─ LocationTracker    GPS + noise filtering + haversine distance    │
│               └─ TrackingState      in-process pub/sub for the bridge             │
│ reminders/  ReminderScheduler (AlarmManager) · ReminderReceiver · BootReceiver    │
│ data/       AppDatabase (SQLite) · Tracking/Medicine/User repositories            │
│ export/     CsvExporter → FileProvider → share sheet                              │
└───────────────────────────────────────────────────────────────────────────────────┘
```

### Why the native layer owns the data

The assessment requires tracking to keep going while the app is minimised, and reminders to work while it is terminated. In both cases the JS runtime may not exist, so anything that has to keep working lives in Kotlin and writes straight to SQLite. React Native is a pure view layer: it sends commands (`startTracking`, `addMedicine`, …), subscribes to live events, and reads history back from the database. Nothing is lost if the RN runtime is destroyed. When the UI comes back, `TrackingContext.sync()` re-reads the native state and the active session's route.

### Login flow

1. `LoginActivity` (Kotlin, Material 3) is the launcher activity. Accounts are local: passwords are hashed with **PBKDF2-HMAC-SHA256** (20k iterations, random 16-byte salt) and compared in constant time.
2. On success the user is saved by `SessionManager` and `MainActivity` (the React Native host) is started.
3. `MainActivity` sends anyone without a session back to login. RN reads the user through `AuthModule.getCurrentUser()`, and `AuthModule.logout()` stops tracking, clears the session and returns to `LoginActivity`.

### Bridge contract

| Module | Methods |
|---|---|
| `TrackingModule` | `startTracking`, `stopTracking`, `getStatus`, `getSessions`, `getSessionDetail`, `deleteSession`, `exportSessionCsv`, `start/stopEnvironmentPreview` · events `TrackingUpdate`, `LocationUpdate` |
| `ReminderModule` | `getMedicines`, `addMedicine`, `setMedicineEnabled`, `deleteMedicine`, `getReminderStatus`, `openExactAlarmSettings`, `scheduleTestReminder` |
| `AuthModule` | `getCurrentUser`, `logout` |

The modules are classic `ReactContextBaseJavaModule`s registered through `BaseReactPackage`. On RN 0.86 they run through the New Architecture interop layer. TypeScript shapes for every payload are in `src/native/types.ts`.

---

## Implementation approach

### Activity detection (`ActivityClassifier.kt`)
- Accelerometer and gyroscope are sampled at `SENSOR_DELAY_GAME` (~50 Hz) on a background `HandlerThread`.
- Every second the classifier looks at a **2-second sliding window** and computes:
  - **σ of the acceleration magnitude**, which measures motion energy and doesn't depend on how the phone is oriented;
  - **step cadence**, from peaks above `mean + 0.5σ` that are at least 250 ms apart;
  - **mean rotation rate** from the gyroscope, to separate a phone lying still from one being carried.
- Rules: stationary if σ < 0.6 m/s² and rotation is low. Running if σ > 4.5, or if cadence > 2.4 steps/s with σ > 3, with GPS speed > 2.6 m/s used only as a tie-breaker. Walking otherwise.
- **Hysteresis:** a new label must win 2 windows in a row before it replaces the current one, so a single jolt doesn't make the activity flicker.
- Time spent in each activity is counted against the monotonic clock (`elapsedRealtime`), so wall-clock changes don't affect it.

### GPS and distance (`LocationTracker.kt`)
- GPS updates every 2 s. A fix is thrown away if its accuracy is worse than 25 m, if it moved less than `max(3 m, accuracy/2)`, or if it implies a speed above 12 m/s (a GPS jump). While the classifier says *stationary*, movements under 10 m are treated as drift.
- Distance is the sum of **haversine** distances between accepted fixes. Every accepted fix is stored, so the route can be redrawn later.

### Background service (`TrackingService.kt`)
- A foreground service with `foregroundServiceType="location"`, a persistent notification (activity · distance · elapsed, plus a **Stop** action) and a partial wake lock so the motion sensors keep delivering events with the screen off.
- It persists a sample (activity, intensity, light, pressure) and session progress every 5 s, and emits a live snapshot every second.
- `START_STICKY`: if the OS kills and restarts the service, it **resumes the active session** from the database (durations, distance and the last route point).

### Medicine reminders (`reminders/`)
- Each medicine has a daily time. `ReminderScheduler` sets one `setExactAndAllowWhileIdle` alarm at **dose time − 15 min**, which fires even in Doze. If the user has revoked exact alarms, it falls back to `setAndAllowWhileIdle`, and the UI shows a banner linking to the setting.
- `ReminderReceiver` posts a high-priority notification, read from SQLite, and **schedules the next day's alarm**, so reminders repeat with no app process running.
- `BootReceiver` re-registers every alarm after a reboot, an app update, or a time or time-zone change.
- There's a **Test** button that fires a reminder after 10 s, so you can check it with the app swiped away.

### Storage (`data/`)
SQLite tables: `users`, `sessions`, `samples` (5-second sensor readings), `locations` (the route) and `medicines`. Indexes on `(session_id, ts)`, foreign keys with cascade delete, and WAL mode so the service can write while the UI reads.

### UI (React Native)
- Bottom tabs (**Dashboard, Map, History, Medicines**) plus a stack screen for session details.
- `TrackingContext` holds the only event subscription and keeps a 90-second rolling series for the live charts. Light and pressure update on the dashboard even when no session is running, through a lightweight sensor preview that pauses while the app is in the background.
- The charts, icons and route sketch are drawn with plain React Native `View`s and Unicode glyphs, so no chart, SVG or icon-font libraries are needed.

---

## Setup

### Prerequisites
- **Node.js ≥ 22.11**
- **JDK 17** (Android Studio's bundled JBR works)
- **Android SDK** with Platform **36** and Build-Tools 36 (install through Android Studio's SDK Manager)
- A physical Android device is strongly recommended. Emulators have no real accelerometer, light or pressure data.

### Google Maps key (optional)
The map uses the Google Maps SDK. Create a key with **Maps SDK for Android** enabled and add it to `android/local.properties`:

```properties
MAPS_API_KEY=your_key_here
```

`local.properties` is git-ignored. Without a key the map tiles stay blank, but the **Sketch** toggle draws the route offline.

### Install and run

```bash
npm install
npm start            # Metro bundler
npm run android      # build & install on a connected device
```

Release APK:

```bash
cd android && ./gradlew assembleRelease
# → android/app/build/outputs/apk/release/app-release.apk
```

### Checks

```bash
npx tsc --noEmit     # type-check (errors inside node_modules/react-native-maps sources are upstream typing issues)
npm run lint
npm test
```

### Permissions requested at runtime
- **Location (precise)**: required to start a session.
- **Notifications** (Android 13+): for the tracking notification and reminders.
- **Exact alarms**: `USE_EXACT_ALARM` on Android 13+; on Android 12 it's covered by `SCHEDULE_EXACT_ALARM`.

---

## Demo video script (2–3 min)
1. **Login (native):** create an account and sign in; show that this screen is a Kotlin Activity.
2. **Dashboard:** live light and pressure readings and charts before tracking starts. Tap **Start tracking**.
3. **Activity detection:** stand still, then walk, then jog. Show the label, its duration and the cadence changing.
4. **Background:** press Home, show the ongoing notification updating, then come back.
5. **Map:** the route drawn live and the distance increasing. Toggle Map / Sketch.
6. **Stop**, then **History**, then open the session: route, breakdown, sensor timeline, **Export CSV**.
7. **Medicines:** add a medicine, then tap **Test**, swipe the app away, and show the notification arriving.

---

## Project structure

```
android/app/src/main/java/com/activitytracker/
  auth/        LoginActivity, SessionManager
  bridge/      TrackingModule, ReminderModule, AuthModule, AppPackage, Mappers
  data/        AppDatabase, Models, Tracking/Medicine/User repositories
  export/      CsvExporter
  notifications/ Notifications (channels, intents)
  reminders/   ReminderScheduler, ReminderReceiver, BootReceiver
  tracking/    TrackingService, ActivityClassifier, SensorHub, LocationTracker, TrackingState
src/
  components/  UI building blocks and charts
  native/      typed bridge wrappers and shared types
  navigation/  tab + stack navigators
  screens/     Dashboard, Map, History, SessionDetail, Medicines
  state/       TrackingContext
  theme/       colours, spacing
  utils/       formatting helpers
```

## Known limitations and possible next steps
- The classifier uses hand-tuned thresholds. A next step would be an on-device model (for example TFLite) trained on labelled windows, or fusion with Android's Activity Recognition API.
- Android 14+ blocks starting a location foreground service from the background. If the OS kills the process, the session resumes the next time the user opens the app rather than in the background.
- Reminders repeat daily. Weekday schedules and "mark as taken" history would be natural additions.
- Android only. The iOS folder is the untouched template.

## Build notes
- **`react-native-maps` is pinned to 1.26.20.** Version 1.26.0 ships a CMake file that skips React Native's standard compile options (`RN_SERIALIZABLE_STATE`), so its Fabric props structs had a different memory layout from `libreactnative.so`. The app crashed with `SIGSEGV` at startup while registering components. 1.26.20 fixes the CMake file.
- If you upgrade a native UI library and the app crashes at launch inside `ComponentDescriptorRegistry::add` / `RawPropsParser::prepare<…Props>`, check that library's `android/src/main/jni/CMakeLists.txt` calls `target_compile_reactnative_options(...)`.
- On Windows, keep the Android SDK and project paths free of spaces where you can (for example, use a directory junction). `sdkmanager` scripts fail on paths with spaces.
