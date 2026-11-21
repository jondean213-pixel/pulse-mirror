# Pulse Mirror

A mirror that beats with your heart. Pulse Mirror turns your phone's front camera into a
calm mirror: a ring around your reflection pulses at your live heart rate, a soft halo
swells and settles with your breathing, and the colour drifts from coral to mint as your
heart-rate variability shows you settling down.

Built with the [Presage SmartSpectra SDK](https://smartspectra.presagetech.com) (Android, v3.5.0)
for the DEV Presage SmartSpectra SDK Challenge.

> Not a medical device. Pulse Mirror is for curiosity and relaxation only. It does not
> diagnose, treat, or monitor any condition.

## Live Presage signals used
| Signal | Where it shows |
|---|---|
| Pulse rate | Ring beat speed + "Pulse bpm" card |
| Breathing (chest trace + rate) | Halo size + "Breaths / min" card |
| HRV (RMSSD) | Ring colour + "Calm" card (Wired / Steady / Calm) |
| Expressions | Gentle nudge line ("Unclench your jaw") |
| Validation status | Coaching hint ("Come a little closer") |

## Setup
Requirements: Android Studio with Android SDK 36.1, JDK 17, a physical Android phone
(API 28+) with a front camera.

1. Get a free API key at the [Presage Developer Portal](https://physiology.presagetech.com/auth/register).
2. Clone this repo and add your key to `local.properties` (never commit it):
   ```
   presageApiKey=YOUR_KEY_HERE
   ```
   (Or set the `PRESAGE_API_KEY` environment variable.)
3. Open the folder in Android Studio, let Gradle sync, plug in your phone, and Run.
   Command line: `./gradlew :app:installDebug`
4. Read the consent screen, tap **I agree**, allow the camera, tap **Start**.
   Pulse fills in after about 12 s, breathing about 30 s, calm (HRV) about 60 s.

## How it works
- `ConsentActivity` — consent gate and full data-handling disclosure. Camera is never
  touched before the user agrees.
- `MirrorActivity` — configures the SDK (front camera, breathing + cardio + expressions,
  `imageOutputEnabled = false`, `enableTelemetry = false`), shows the preview full-screen,
  and feeds live metrics to the overlay.
- `PulseRingView` — draws the heartbeat ring (an exponential "beat" envelope repeating at the
  measured bpm) and the breathing halo (chest trace normalised over the last ~300 samples).
- `CalmScore` — maps RMSSD 20–80 ms to a 0–1 "calm" value for colour only. In-memory
  `SessionStats` gives a one-line "Last session" summary and is cleared on every start.

## Consent & Data Handling
- **Camera consent:** the app opens on a consent screen. The camera starts only after the
  user taps "I agree" and grants Android's camera permission. Tapping Stop or leaving the
  app stops measurement.
- **What is captured:** live front-camera frames, processed on-device by the SmartSpectra
  SDK to compute pulse, breathing, HRV and expression. The app never records video or
  photos; SDK image output is disabled.
- **Storage:** nothing is written to disk. No account, no history, no database.
  `android:allowBackup="false"`.
- **Sharing:** the app shares nothing with third parties. The SDK contacts Presage's service
  to authenticate the API key over an encrypted connection.
- **Retention & deletion:** readings exist only in memory while the screen is open and are
  discarded when the app closes. There is nothing to delete.
- **SDK telemetry:** the SDK's diagnostic telemetry is on by default; Pulse Mirror sets
  `enableTelemetry = false`, so the SDK starts no telemetry session.
- **AI services:** none. The SDK's optional LLM Insights feature is not used.
- **Safety:** no diagnosis, treatment claims, or medical decision-making; no covert or
  background monitoring (the camera runs only on a visible, foreground screen).

## Build in CI
`.github/workflows/android.yml` builds a debug APK on every push. Add the key as a
repository secret named `PRESAGE_API_KEY`; without it the APK builds but cannot
authenticate.

## License
MIT for Pulse Mirror's own code. The SmartSpectra SDK is licensed separately by Presage
Technologies.
