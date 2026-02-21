# 🐾 OpenClaw Android Node

Turn your Android phone into a full OpenClaw agent node. Your AI agents get direct control over the device — camera, location, screen automation, notifications, and more.

## Install

1. Go to [Actions](../../actions) → latest build → download `openclaw-node-debug` artifact
2. Unzip, transfer `app-debug.apk` to your phone
3. Enable "Install from unknown sources" in Settings
4. Install the APK
5. Grant permissions when prompted

## Setup

1. Open the app
2. Enter your gateway URL (e.g., `ws://10.100.0.1:18789`)
3. Enter your auth token
4. Name your node (e.g., `dj-phone`)
5. Tap **Connect**

For screen automation: Settings → Accessibility → OpenClaw → Enable

## Capabilities

| Capability | Actions |
|-----------|---------|
| 📸 Camera | Snap front/back photos |
| 📍 Location | GPS coordinates |
| 🔔 Notifications | Push alerts from agents |
| 📱 Screen Control | Tap, swipe, type in any app |
| 📁 Files | Read/write/list Downloads |
| 🔋 Sensors | Battery, WiFi, storage, memory |
| 🔦 Flashlight | Toggle on/off |
| 📳 Vibration | Custom duration |
| 🗣️ TTS | Speak text aloud |
| 💬 SMS | Send/read messages |
| 📞 Calls | Initiate phone calls |

## Architecture

```
Phone ←WebSocket→ OpenClaw Gateway ←→ Your Agents (Scout, Ranger, etc.)
```

- Foreground service keeps connection alive
- Auto-reconnect with exponential backoff
- WireGuard tunnel for security
- Accessibility service for full screen automation

## Build Locally

```bash
./gradlew assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
```

Requires JDK 17 and Android SDK.

## License

MIT
