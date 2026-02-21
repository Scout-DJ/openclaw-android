# OpenClaw Android — Agent Node

An Android app that turns your phone into a full OpenClaw node, giving agents (Scout, Ranger, etc.) direct control over the device.

## What Agents Can Do

### Tier 1 — Core (MVP)
- **Notifications** — Push alerts, silent/priority/timeSensitive
- **Camera** — Snap front/back/both, video clips
- **Location** — GPS (coarse/balanced/precise), geofencing
- **Screen** — Screenshots, screen recording
- **Files** — Read/write/list device storage
- **Clipboard** — Read/write
- **TTS** — Speak text aloud on the phone
- **Sensors** — Battery, WiFi, Bluetooth, accelerometer, light

### Tier 2 — Power User
- **Phone calls** — Initiate calls, read call log
- **SMS** — Send/read messages
- **Contacts** — Read/search contacts
- **Calendar** — Read/create events
- **Media** — Play audio, control volume
- **Flashlight** — Toggle
- **Vibration** — Patterns

### Tier 3 — Full Control (Accessibility Service)
- **Screen automation** — Tap, swipe, type into any app (like xdotool on Pi)
- **App launch** — Open any app by package name
- **UI inspection** — Read screen content (accessibility tree)
- **Auto-respond** — Reply to notifications programmatically

## Architecture

```
┌──────────────────────────────────────┐
│           OpenClaw Gateway           │
│         (substation:18789)           │
└──────────┬───────────────────────────┘
           │ WebSocket (wss://)
           │ + WireGuard tunnel
┌──────────▼───────────────────────────┐
│        OpenClaw Android App          │
│                                      │
│  ┌─────────────┐  ┌──────────────┐  │
│  │ Node Client  │  │  Agent Chat  │  │
│  │ (WebSocket)  │  │   (WebView)  │  │
│  └──────┬──────┘  └──────────────┘  │
│         │                            │
│  ┌──────▼──────────────────────────┐│
│  │       Capability Bridge         ││
│  │  camera | location | screen     ││
│  │  files  | sensors  | a11y       ││
│  │  notify | sms | calls | media   ││
│  └─────────────────────────────────┘│
│                                      │
│  ┌─────────────────────────────────┐│
│  │    Foreground Service (24/7)    ││
│  │    Persistent notification      ││
│  └─────────────────────────────────┘│
└──────────────────────────────────────┘
```

## Tech Stack

- **Language:** Kotlin
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 35 (Android 15)
- **Build:** Gradle + Kotlin DSL
- **Networking:** OkHttp WebSocket → OpenClaw Gateway
- **UI:** Jetpack Compose (minimal — mostly headless service)
- **DI:** Manual (small app, no Hilt needed)

## Protocol

The app acts as an OpenClaw **node**. It:
1. Connects to the gateway via WebSocket
2. Announces capabilities (camera, location, screen, etc.)
3. Receives commands from agents (JSON-RPC style)
4. Executes locally, returns results

### Command Format (from gateway)
```json
{
  "id": "cmd_123",
  "action": "camera_snap",
  "params": {
    "facing": "back",
    "quality": 80,
    "maxWidth": 1920
  }
}
```

### Response Format (to gateway)
```json
{
  "id": "cmd_123",
  "status": "ok",
  "data": {
    "path": "/media/snap_123.jpg",
    "width": 1920,
    "height": 1080
  },
  "attachment": "<base64 jpeg>"
}
```

## Permissions Required

```xml
<!-- Tier 1 -->
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />

<!-- Tier 2 -->
<uses-permission android:name="android.permission.READ_CONTACTS" />
<uses-permission android:name="android.permission.READ_CALENDAR" />
<uses-permission android:name="android.permission.SEND_SMS" />
<uses-permission android:name="android.permission.READ_SMS" />
<uses-permission android:name="android.permission.CALL_PHONE" />
<uses-permission android:name="android.permission.READ_CALL_LOG" />
<uses-permission android:name="android.permission.FLASHLIGHT" />
<uses-permission android:name="android.permission.VIBRATE" />

<!-- Tier 3 — declared in accessibility service XML -->
<!-- android.accessibilityservice.AccessibilityService -->
```

## Security Model

- **WireGuard only** — App connects through VPN tunnel, never over public internet
- **Gateway token auth** — Same auth as other nodes
- **Permission gating** — Each capability requires explicit Android permission grant
- **Agent allowlist** — Only approved agents can send commands
- **Audit log** — Every command logged locally with timestamp
- **Kill switch** — Physical volume button combo to disconnect instantly

## Screens

1. **Setup** — Gateway URL, auth token, WireGuard config import
2. **Dashboard** — Connection status, active agent, command log
3. **Permissions** — Grant/revoke per capability
4. **Agent Chat** — WebView to chat directly with your agent
5. **Settings** — Node name, auto-connect, battery optimization bypass

## Build Plan

### Phase 1 — Skeleton (Day 1)
- [ ] Android project scaffold (Kotlin, Compose, Gradle KTS)
- [ ] Foreground service with persistent notification
- [ ] WebSocket client connecting to gateway
- [ ] Basic handshake + heartbeat

### Phase 2 — Core Capabilities (Day 2-3)
- [ ] Camera snap (front/back)
- [ ] Location (GPS)
- [ ] Notifications (receive from agent, display)
- [ ] Screen capture
- [ ] File operations
- [ ] Sensor readings (battery, wifi, etc.)

### Phase 3 — Power Features (Day 4-5)
- [ ] Accessibility service for screen automation
- [ ] SMS/call integration
- [ ] Contact/calendar read
- [ ] Media playback
- [ ] TTS

### Phase 4 — Polish (Day 6-7)
- [ ] Setup wizard
- [ ] Permission flow
- [ ] Dashboard UI
- [ ] Agent chat WebView
- [ ] Kill switch
- [ ] Audit log viewer

## File Structure
```
openclaw-android/
├── app/
│   ├── src/main/
│   │   ├── java/ai/openclaw/node/
│   │   │   ├── App.kt
│   │   │   ├── MainActivity.kt
│   │   │   ├── service/
│   │   │   │   ├── NodeService.kt          # Foreground service
│   │   │   │   ├── AccessibilityAgent.kt   # Screen automation
│   │   │   │   └── WatchdogReceiver.kt     # Restart on boot
│   │   │   ├── gateway/
│   │   │   │   ├── GatewayClient.kt        # WebSocket connection
│   │   │   │   ├── Protocol.kt             # Command/response models
│   │   │   │   └── Auth.kt                 # Token management
│   │   │   ├── capabilities/
│   │   │   │   ├── CameraCapability.kt
│   │   │   │   ├── LocationCapability.kt
│   │   │   │   ├── ScreenCapability.kt
│   │   │   │   ├── FileCapability.kt
│   │   │   │   ├── SensorCapability.kt
│   │   │   │   ├── NotifyCapability.kt
│   │   │   │   ├── SmsCapability.kt
│   │   │   │   ├── MediaCapability.kt
│   │   │   │   └── A11yCapability.kt
│   │   │   └── ui/
│   │   │       ├── SetupScreen.kt
│   │   │       ├── DashboardScreen.kt
│   │   │       └── theme/Theme.kt
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── ARCHITECTURE.md
```
