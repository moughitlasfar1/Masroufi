# Masroufi (`v1.2.0`)

**Masroufi** is a fast, offline-first, privacy-focused personal finance, pay-cycle budgeting, Moroccan fiscal planning, and credit simulation Android app built with Kotlin and Jetpack Compose (Material 3 Expressive). Designed for everyday use in Morocco, it supports **Français**, **العربية** (full RTL), **الدارجة المغربية**, and **English** with Moroccan Dirham (`DH` / `MAD`) formatting.

---

## Key Features

### Pay-Cycle Budgeting & Daily Guidance
- **Pay-Cycle Safe-to-Spend (*Reste à dépenser aujourd'hui*)**: Computes your daily safe spending allowance around custom salary days (`1..28`), factoring in recurring bills, rollover envelopes, and remaining days in the cycle.
- **Live Purchase Impact Preview**: Shows how a prospective expense affects your daily allowance, envelope limits, and projected cycle end date before saving.
- **4 Focused Navigation Tabs**:
  - **Accueil**: Safe-to-spend hero card, live cycle progress, quick presets, pending bank alerts, and recent transactions.
  - **Analyses**: Weekly bar chart, monthly category breakdown, unified pace projection, and "What-If" simulators.
  - **Plan**: Multi-account balances (`Total des comptes`, `Solde du cycle`, `Épargne du cycle`), **Simulateur de Crédit**, **Annual Bills Reserve (Vignette)**, **Zakat & Hawl Tracker**, category envelopes, recurring bills, savings goals, and Moroccan social finance tools (*Daret*, *Sellef* with WhatsApp share cards).
  - **Activité**: Friendly date grouping, swipe actions, search/filtering, and encrypted backup/export tools.

### Fast Capture, Hardware Shortcuts & Smart Detection
- **5-Second Expense Entry**: Translucent overlay with large numeric keypad, 1-tap Voice switch, time-aware shortcuts, top recent category chips, and automatic category learning.
- **Double-Tap Power / Side Button (`Masroufi +`)**: Opt-in launcher shortcut (`MasroufiQuickAddLauncherAlias`) toggleable in **Settings** so you can map double-pressing the Power/Side button (Samsung Side button, Xiaomi/Redmi Button shortcuts, OnePlus, etc.) straight to **Add Expense**.
- **Quick Settings Tiles & Google Assistant App Actions**: 3 Quick Settings tiles (*Add Expense*, *Voice*, *Left Today*) with Android 13+ add-tile prompts, static/dynamic shortcuts, and Google Assistant App Actions (`"Hey Google, add an expense in Masroufi"`).
- **Multilingual Voice Capture**: Walkie-talkie voice entry supporting Darija, Arabic, French, and English keywords (`qahwa`, `gheda`, `echa`, `dwa`, `khobz`, `طاكسي`, etc.) plus spoken budget queries.
- **Bank Notification Detection**: Privacy-preserving on-device parsing for Moroccan bank SMS/app notifications (card purchases, ATM withdrawals, incoming/outgoing transfers) with OTP/3DS filtering and user review.
- **Home-Screen Widgets & Quick Capture Notification**: Customizable glass/solid widgets (`Smart Slot`, `Left Today`, `Quick Add`, `1×1 Mic`, `Envelopes`, `Bills & Daret`, `Goal & Cycle`) synced automatically with your pay cycle.

### Moroccan Fiscal Tools (`com.masroufi.fiscal`)
Isolated in package `com.masroufi.fiscal` with pure Kotlin calculators and unit tests:
- **Annual Bills Reserve (Vignette TSAVA, Car Insurance & Custom)**:
  - Turns yearly lump sums into monthly reserves (`remaining / monthsLeft`) with progress bars and edge-case handling (due this month, overdue, next-year rollover).
  - Starter Vignette rates centralized in `FiscalRates.kt` with an `"as of"` date and DGI source note (`< 8 CV` & `8–10 CV`; `11–14 CV` and `15+ CV` kept as `TODO` placeholders with manual MAD override).
  - Backed by Masroufi's existing **Savings Goal** + monthly **Recurring Bill**, with a **Mark paid** dialog to log the expense in **Activité** and close the reserve.
- **Zakat & Hijri Hawl Tracker**:
  - Calculates **Eligible wealth = Accounts + Savings − short-term borrowed Sellef debts** and `2.5%` Zakat estimate.
  - Supports **Gold (`85g`)** and **Silver (`595g`)** Nisab from offline user-editable MAD/g prices with an `"as of"` date (no network calls).
  - Tracks lunar **Hawl** days remaining via `java.time.chrono.HijrahDate` (`Below Nisab`, `Hawl running`, `Due now`), includes a brief scholar disclaimer, and offers a **"Set aside monthly"** CTA reusing the Annual Bills reserve engine.

### Daret & Sellef WhatsApp Share Cards
- Generates glass-styled off-screen PNG summary cards from existing **Daret** and **Sellef** screens with proper Arabic/Darija RTL shaping (`Readex Pro`) and French LTR styling (`Plus Jakarta Sans`).
- Supports language switching (**Français**, **العربية**, **الدارجة**) and a **Hide amounts (`••••`)** toggle at share time.
- Saves PNGs exclusively in `cacheDir/shared_cards/` (scrubbing previous images automatically) and shares via a strictly scoped `FileProvider` preferring WhatsApp (`com.whatsapp` / `com.whatsapp.w4b`) with a plain-text fallback.

### Moroccan Credit Simulator (*Simulateur de Crédit* — BCP Rules)
Accessible from both the **Dashboard Header** and the **Plan** tab, isolated in package `com.masroufi.credit`:
- **Moroccan Banking Presets**: **Consommation** (`7.5%` HT, up to `84`/`120` months, `500 000 MAD` ceiling), **Auto (Neuve)** (`6.0%` HT, up to `60` months), and **Immobilier** (`4.5%` HT, up to `300` months).
- **Tiered Debt-to-Income (DTI) Ceilings**: `45%` / `50%` without mortgage; `50%` / `55%` / `60%` with mortgage.
- **HT + 10% Moroccan TVA & Amortization**: Constant TTC installments with `HT` / `TVA 10%` breakdown, safe `0%` handling, `0–3` month partial grace period (*différé*), monthly ADI insurance, and month-by-month amortization table.
- **Regulatory Alerts & Budget Integration**: Flags over-indebtedness, borrower age `> 70`, SMIG threshold, and mandatory ADI; links scenarios into Masroufi as a recurring monthly bill in one tap.

### Privacy, Security & Sync
- **Encrypted On-Device Storage**: SQLCipher-encrypted Room database (`sqlcipher-android`) plus Android Keystore **AES-256-GCM** `SecureStore` for widget snapshots, fiscal settings, and notification caches.
- **App Lock, `FLAG_SECURE` & Masking Everywhere**: One-tap `••••` amount masking, `FLAG_SECURE` screen protection, and 6–8 digit PBKDF2-HMAC-SHA256 App Lock with biometric unlock and progressive lockout. Audited across widgets, `WidgetConfigActivity`, Quick Settings tiles (lock-screen masking + `unlockAndRun`), dynamic shortcuts, `VISIBILITY_PRIVATE` notifications, and shared cards.
- **Authentication & Optional Cloud Sync**: Google Sign-In via Jetpack `CredentialManager` with Cloud Firestore sync (zero-trust owner-only security rules + Firebase App Check), or offline Local Device Mode.
- **Encrypted Backups & Safe Exports**: Password-protected **AES-256-GCM** JSON backups with preview/rollback protection, plus formula-injection-neutralized CSV export.

---

## Architecture & Tech Stack

- **Language & UI**: Kotlin, Jetpack Compose (Material 3 Expressive), Edge-to-Edge, Adaptive Light/Dark/System themes, bundled `Plus Jakarta Sans` & `Readex Pro` fonts, full RTL support.
- **Architecture**: MVVM with `ViewModel`, `StateFlow`, and Kotlin Coroutines/Flow.
  - Core app: `com.example` (`data`, `domain`, `ui`, `notifications`, `security`, `voice`, `widget`)
  - Isolated Credit Simulator module: `com.masroufi.credit` (`domain`, `data`, `presentation`)
  - Isolated Fiscal & Zakat module: `com.masroufi.fiscal` (`domain`, `data`, `presentation`)
- **Persistence & Backend**: SQLCipher Room (local-first), Android Keystore `SecureStore`, WorkManager, Firebase Auth, Cloud Firestore (zero-trust rules), and Firebase App Check (`PlayIntegrity` in release).

---

## Build, Test & Release

### Local Development
> Note: Use `gradle` directly (the Gradle wrapper is not required in the build container).

```bash
# Compile debug APK
gradle :app:assembleDebug

# Run unit & Robolectric test suites
gradle :app:testDebugUnitTest

# Run focused feature & security test suites
gradle :app:testDebugUnitTest --tests "com.masroufi.fiscal.FiscalCalculatorsTest"
gradle :app:testDebugUnitTest --tests "com.example.ShareCardAndPrivacyAuditTest"
gradle :app:testDebugUnitTest --tests "com.example.QuickTileAndShortcutsStep1Test"
gradle :app:testDebugUnitTest --tests "com.example.SecurityAuditHardeningTest"
gradle :app:testDebugUnitTest --tests "com.masroufi.credit.CreditCalculatorTest"
```

### Firestore Security Rules Testing
Requires Firebase CLI and Node.js:

```bash
# Start Firestore emulator on 127.0.0.1:8085 and execute security rules tests
firebase emulators:start --only firestore
FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
```

### Production Release (GitHub Actions)
Signed, R8-minified production builds (**AAB & APK**) are automated via `.github/workflows/release-production.yml`:
1. Configure secrets in **Repository Settings → Environments → `production`**:
   - `RELEASE_KEYSTORE_BASE64`
   - `STORE_PASSWORD`
   - `KEY_PASSWORD`
   - `GOOGLE_SERVICES_JSON`
2. Trigger via a version tag push (e.g., `v1.2.0`) or manual `workflow_dispatch` in GitHub Actions.
3. Both the signed **APK** and **AAB** are published to the GitHub Release and uploaded as workflow artifacts.
