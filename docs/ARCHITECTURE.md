# Masroufi — System Architecture

## 1. High-Level Layers

- **Presentation (`com.example.ui`, `com.masroufi.credit.presentation`)**:
  - Jetpack Compose (Material 3) with Edge-to-Edge insets, adaptive Dark/Light/System themes, and custom accent palettes.
  - `MainActivity` hosts `AppNavigation` → `MainFeatureScreen` with a 4-tab `HorizontalPager` (`vue`, `semaine`, `categories`, `historique`), single `ActiveSheet` modal coordinator, and isolated full-screen overlay for `CreditSimulatorScreen`.
  - ViewModels (`MasroufiViewModel`, `CreditSimulatorViewModel`) expose immutable `StateFlow` UI state collected via `collectAsStateWithLifecycle()`.
- **Domain (`com.example.domain`, `com.masroufi.credit.domain`)**:
  - Pure Kotlin engines with zero Android framework dependencies: `PayCycleEngine`, `VoiceDarijaParser`, `BankMessageParser`, `CreditCalculator`.
  - Runtime 4-language localization tables (`Translations.kt`, `CreditStrings.kt`) supporting **Français**, **العربية** (RTL), **الدارجة المغربية**, and **English** with instant in-app switching (no XML locale resources).
- **Data (`com.example.data`, `com.example.data.local`, `com.masroufi.credit.data`)**:
  - **Repository Boundary**: `MasroufiDataSource` interface implemented by `RoomMasroufiRepository` (encrypted local Room + best-effort Firestore write-through), `MasroufiRepository` (legacy Firestore fallback), and `SwitchingMasroufiRepository` (runtime router controlled by `DataSourceConfigStore` + Firestore fallback kill switch).
  - **Local Encrypted DB (Phase 1a–1b)**: Room `2.7.0` + SQLCipher `4.20.0` (`MasroufiDatabase`, `MasroufiDao`, `MasroufiDatabaseProvider`) with strict `Source.SERVER` one-time read-only `FirestoreToRoomImporter`.
  - **Cloud Firestore**: Named database (`R.string.firestore_database_id`) with zero-trust owner-only security rules (`firestore.rules`) and Firebase App Check. Transitioning to an optional, opt-in client-encrypted sync layer in Phase 2.
- **Security (`com.example.security`)**:
  - `SecureStore`: AndroidKeyStore AES-256-GCM key-value wrapper with key-name AAD binding.
  - `DatabaseKeyManager`: Manages the 256-bit SQLCipher passphrase inside `SecureStore`.
  - `AppSecurityManager`: PIN/Biometric App Lock, `FLAG_SECURE`, AES-256-GCM backup encryption/decryption, and clipboard auto-clear.
- **Voice & System Surfaces (`com.example.voice`, `com.example.notifications`, `com.example.widget`)**:
  - `MoonshineVoiceController`: Offline-preferred `SpeechRecognizer` + 16-bar RMS waveform + `TextToSpeech`.
  - `BankNotificationListenerService` & `WorkManager` smart notifications.
  - `MasroufiAppWidget`, `QuickCaptureActivity`, and 3 Quick Settings Tiles.

## 2. Dependency Injection

- Lightweight manual constructor injection via Jetpack `viewModelFactory { initializer { ... } }` inside `MainActivity.kt` (no Hilt/Koin boilerplate).

## 3. Cloud / GMS Isolation Policy (Phase 2)

- Because AI Studio's Android build container prohibits Gradle `productFlavors`, Masroufi isolates all Google/Firebase integrations behind clean interfaces (`CloudSyncRepository`, `VoiceIntegrationBridge`) in `src/main`, ensuring:
  - Full offline-first operation without requiring Google Sign-In.
  - Strictly opt-in cloud sync with client-side AES-256-GCM field encryption (Phase 2).
  - Clean boundary if an external F-Droid / FOSS build strips GMS dependencies in CI.
