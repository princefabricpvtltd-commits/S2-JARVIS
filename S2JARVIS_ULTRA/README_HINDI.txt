S2 JARVIS ULTRA v3.0 — Siri-inspired voice assistant
=====================================================

WHAT'S INCLUDED
- Siri-inspired animated cyan orb and dark interface
- Hindi, English (India), Punjabi speech input and Android TTS replies
- Tap-to-talk microphone and typed AI chat
- Speech-based “Hey S2” wake listener with a persistent foreground notification
- Local commands: time/date, YouTube, WhatsApp, Camera, Chrome, Maps, Dialer,
  Settings, Alarm screen, Google Search and websites
- Gemini-powered online AI backend; API key stays on the backend
- Recent conversation context for the current app session

ANDROID APP SETUP
1. Extract this ZIP and open the S2JARVIS_ULTRA folder in Android Studio.
2. Install Android SDK Platform 35 and allow Gradle Sync to finish.
3. Run on a real Android phone or emulator; grant microphone permission.
4. Allow notifications so the wake-word foreground notification is visible.
5. Tap ENABLE HEY S2 while the app is visible. Say “Hey S2”. Tap the notification
   to reopen the app if it was in the background.
6. Tap STOP VOICE to stop listening and disable the wake-word service.

ONLINE AI BACKEND
1. Install Node.js 18+ on your computer/server.
2. Open the backend folder in a terminal and run: npm install
3. Copy .env.example to .env and add your Gemini API key.
4. Run: npm start
5. Check: http://127.0.0.1:3000/health
6. In the app, set AI CONNECTION to your backend /chat URL. For an Android emulator
   use http://10.0.2.2:3000/chat. For a physical phone, use your PC's LAN IP, e.g.
   http://192.168.x.x:3000/chat (phone and PC must share Wi-Fi).
7. Use HTTPS, authentication, rate limiting and firewall rules for public deployment.

IMPORTANT WAKE-WORD LIMITATIONS
- “Hey S2” in this build uses Android SpeechRecognizer repeatedly inside a
  microphone foreground service. It is a functional prototype, not a dedicated
  low-power/offline wake-word engine. It may use battery and may require internet
  or a supported recognition provider/language pack.
- Android 12+ restricts starting microphone foreground services from the background.
  Start Hey S2 from the visible app. Android 14+ also enforces microphone foreground
  service permissions. Device vendors may stop services under aggressive battery rules.
- When the wake phrase is detected while the app is in the background, Android may
  require the user to tap the notification to return to the app; background activity
  launches are not guaranteed. When the app is visible, it switches to command listening.
- Speech recognition is provider-dependent and can mishear “S2”. Try “Hey S two” or
  “Hey ess two” if needed. For reliable production wake detection, integrate a dedicated
  wake-word SDK/engine and configure its license/access key.
- Android Text-to-Speech uses the voices installed on the device. A specific Iron Man
  voice is not bundled; choose an available male voice in Android TTS settings.
- Calls are opened in the dialer for user confirmation. The app does not silently place
  calls or send messages. Protected actions require Android permissions/user approval.
- Keep GEMINI_API_KEY on the backend only; never embed it in the APK.

PROJECT
Package: com.s2jarvis.ultra
Version: 3.0 (versionCode 3)
minSdk: 23 | targetSdk: 35 | compileSdk: 35


BUILD FIX v3.0.2:
- Java compile target and Kotlin compile target are both pinned to JVM 1.8.
- App version updated to 3.0.2.
- If Android Studio still reports a mismatch, confirm that you opened this extracted folder,
  then use File > Sync Project with Gradle Files and Build > Clean Project.
\n\nSIRI-STYLE UI UPDATE v4.0\n- Redesigned the main screen with a dark premium Siri-inspired interface.\n- Added a luminous animated multi-colour orb, compact status pill, chat-style response card, rounded message composer and matching voice controls.\n- Existing voice, Hey S2, language, backend and quick-command IDs/features are retained.\n- This is a visual redesign; it does not remove Android background microphone restrictions or the need to configure a reachable AI backend.\n