# 📞 Ultra-Lightweight Group Call App

Android group video calling app (2-7MB APK) with WebRTC P2P communication.

## GitHub Actions Build

Automatically build APK on every push:

1. Push code to GitHub
2. Go to **Actions** tab
3. Select "Build Android APK" workflow
4. Download APK from artifacts

Or manually trigger:
- Go to Actions → Build Android APK → Run workflow

## Project Structure

```
new-project/
├── server.js              # Node.js signaling server
├── package.json           # Server dependencies
└── android/               # Android app
    ├── app/
    │   ├── src/main/
    │   │   ├── AndroidManifest.xml
    │   │   ├── java/com/groupcall/app/
    │   │   │   ├── MainActivity.java
    │   │   │   └── CallActivity.java
    │   │   └── res/layout/
    │   │       ├── activity_main.xml
    │   │       └── activity_call.xml
    │   ├── build.gradle
    │   └── proguard-rules.pro
    ├── build.gradle
    ├── settings.gradle
    └── gradle.properties
```

## Setup Server

```bash
# Install dependencies
npm install

# Start server (port 3000)
npm start
```

## Build Android App

1. Open `android/` folder in Android Studio
2. Change `SERVER_URL` in `CallActivity.java` to your server IP
3. Build APK: `Build → Build Bundle(s) / APK(s) → Build APK(s)`

## Features

- ✅ Group video calls (multiple users)
- ✅ P2P WebRTC communication
- ✅ Ultra-small APK (~2-7MB)
- ✅ Mute/Unmute audio
- ✅ Camera on/off toggle
- ✅ Create/Join rooms
- ✅ Minimal dependencies

## APK Size Optimization

- Minification enabled (ProGuard)
- Resource shrinking
- Only ARM64/ARMv7 ABIs
- Minimal dependencies
- No heavy frameworks

## Server Configuration

Edit `server.js` port:
```javascript
const PORT = process.env.PORT || 3000;
```

Edit Android app server URL in `CallActivity.java`:
```java
private static final String SERVER_URL = "http://YOUR_SERVER_IP:3000";
```

## Notes

- Server must be accessible from Android device (same network or public IP)
- Uses Google STUN servers for NAT traversal
- Minimum Android version: 7.0 (API 24)
# QwanR
