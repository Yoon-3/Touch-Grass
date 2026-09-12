# Touch Grass

24-hour hackathon Android app. Blocks a set of chosen apps until the user
completes a daily outdoor-photo mission generated and graded by the Gemini
API.

## How it works

1. **Setup** (`MainActivity`) - grant Accessibility Service, "display over
   other apps", and Camera permissions, then pick which apps to block.
2. **Activate** - calls Gemini to generate today's mission (a short outdoor
   photo task), stores it, sets the app to "locked", and schedules a
   midnight alarm.
3. While locked, `TouchGrassAccessibilityService` watches for any blocked
   app coming to the foreground and immediately launches
   `BlockOverlayActivity` full-screen on top of it (back button is disabled
   so the user can't peek behind it).
4. On the overlay, the user taps **Take Photo**, the photo + mission text
   are sent to Gemini (`GeminiRepository.verifyPhoto`), which replies
   `APPROVE` or `REJECT`. Approve unlocks the app for the rest of the day;
   reject asks for another photo.
5. `MidnightResetReceiver` fires every night just after midnight, re-locks
   the app, and asks Gemini for a fresh mission.

## Setup

1. Open the project root in Android Studio (Koala/Ladybug or newer).
2. Copy `local.properties.example` to `local.properties` (Android Studio
   will also add `sdk.dir` there automatically) and set:
   ```
   GEMINI_API_KEY=your_key_here
   ```
   Get a free key at https://aistudio.google.com/apikey
3. Run on a device or emulator with Play services (API 26+). A real device
   is strongly recommended for the demo since you need to actually walk
   outside and take a photo.
4. In the app: grant all three permissions, select a couple of apps to
   block (e.g. Instagram, YouTube), tap **Activate**.
5. Open one of the blocked apps - the lock screen with the mission should
   appear immediately.

## Known hackathon shortcuts / things to mention to judges

- The Gemini API key is called directly from the client for demo speed.
  In production this should go through a backend proxy so the key isn't
  bundled in the APK.
- `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` is declared, but some OEMs
  (Samsung, Xiaomi, etc.) aggressively kill background alarms/services -
  for the demo, use the **Deactivate**/**Activate** buttons to simulate a
  "new day" instead of waiting for real midnight.
- Accessibility services can be disabled by the OS on low-RAM devices to
  save battery - if the lock stops triggering, check
  Settings > Accessibility > Touch the Grass is still on.
- There's no photo-history or streak tracking yet - `AppStateManager`
  only tracks today's mission and lock state, which is enough for the
  core demo loop.
- The mission-verification prompt is deliberately strict about "must look
  outdoors, not a screenshot" to reduce cheating with old photos, but
  Gemini's judgment isn't perfect - worth having a backup phone/photo
  ready in case a demo photo gets a false reject.

## Project structure

```
app/src/main/java/com/touchgrass/app/
  MainActivity.kt                    - setup UI (permissions, app picker, activate)
  BlockOverlayActivity.kt            - full-screen lock + camera + verification UI
  TouchGrassAccessibilityService.kt  - detects blocked app coming to foreground
  MidnightResetReceiver.kt + AlarmScheduler - daily reset
  GeminiRepository.kt                - Gemini API calls (mission gen + photo verify)
  AppStateManager.kt                 - SharedPreferences-backed state
```
