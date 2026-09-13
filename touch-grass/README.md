# Touch Grass

24-hour hackathon Android app. Blocks a set of chosen apps until the user
completes a daily outdoor-photo mission generated and graded by the Gemini
API.

## How it works

1. **Setup** (`MainActivity`) - grant Accessibility Service, "display over
   other apps", and Camera permissions, then pick which apps to block.
2. **Activate** - calls Gemini to generate today's mission (a short outdoor
   photo task), stores it, clears everyone's spent time, turns blocking on,
   and schedules a midnight alarm. Each blocked app gets its own daily time
   budget, 30 minutes by default, editable per app in the picker.
3. While blocking is on, `TouchGrassAccessibilityService` banks how long
   each blocked app spends in the foreground and launches
   `BlockOverlayActivity` full-screen over it once that app's budget runs
   out - immediately when you switch to an app that is already over, or via
   a callback timed to the moment it runs out while you keep using it (back
   button is disabled so the user can't peek behind it).
4. On the overlay, the user taps **Take Photo**, the photo + mission text
   are sent to Gemini (`GeminiRepository.verifyPhoto`), which replies
   `APPROVE` or `REJECT`. Reject asks for another photo. Approve clears
   that app's spent time, handing it a full budget again - except on a
   0-minute limit, where there is no budget to hand back and clearing
   alone would re-block on the next foreground event, so the photo
   unlocks that app for the rest of the day instead.
5. `MidnightResetReceiver` fires every night just after midnight, clears
   every blocked app's spent time budget and asks Gemini for a fresh
   mission. It also runs on `BOOT_COMPLETED`, because AlarmManager alarms
   don't survive a reboot and the daily chain would otherwise stay broken
   until the user re-activated by hand. Both paths share one guard on the
   last reset date, so a same-day reboot only re-arms the alarm, and a
   midnight missed while the phone was off is caught on the next delivery
   instead of being lost.

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
5. Open one of the blocked apps - the lock screen appears once that app's
   daily budget runs out. For a demo you don't want to wait 30 minutes for,
   set the app's **min** field to `0` before activating and the lock screen
   comes up the moment you open it. On a 0-minute limit one approved photo
   unlocks that app for the rest of the day; the next midnight reset - or a
   **Deactivate**/**Activate** cycle - locks it again.

## Known hackathon shortcuts / things to mention to judges

- The Gemini API key is called directly from the client for demo speed.
  In production this should go through a backend proxy so the key isn't
  bundled in the APK.
- `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` is declared, but some OEMs
  (Samsung, Xiaomi, etc.) aggressively kill background alarms/services. A
  killed alarm no longer loses the day - the next `BOOT_COMPLETED` or
  midnight delivery still sees a stale reset date and rolls over then - but
  the rollover can land late. For the demo, use the **Deactivate**/
  **Activate** buttons to simulate a "new day" instead of waiting for real
  midnight.
- Accessibility services can be disabled by the OS on low-RAM devices to
  save battery - if the lock stops triggering, check
  Settings > Accessibility > Touch Grass is still on.
- There's no photo-history or streak tracking yet - `AppStateManager`
  only tracks today's mission, each blocked app's time budget and spent
  time, and which apps a photo has already unlocked today, which is enough
  for the core demo loop.
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
  TouchGrassApp.kt                   - Application + manual DI container
```
