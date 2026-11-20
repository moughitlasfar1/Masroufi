# Masroufi

**Masroufi** is a fast, privacy-first personal finance, pay-cycle budgeting, and Moroccan credit simulation Android app built with Kotlin and Jetpack Compose (Material 3). Designed for everyday use in Morocco, it supports **Français**, **العربية** (full RTL), **الدارجة المغربية**, and **English** with Moroccan Dirham (`DH` / `MAD`) formatting.

---

## Key Features

### Pay-Cycle Budgeting & Daily Guidance
- **Pay-Cycle Safe-to-Spend (*Reste à dépenser aujourd'hui*)**: Computes your daily safe spending allowance around custom salary days (`1..28`), factoring in recurring bills, rollover envelopes, and remaining days in the cycle.
- **Live Purchase Impact Preview**: Shows how a prospective expense affects your daily allowance, envelope limits, and projected cycle end date before saving.
- **4 Focused Navigation Tabs**:
  - **Accueil**: Safe-to-spend hero card, live cycle progress, quick presets, pending bank alerts, and recent transactions.
  - **Analyses**: Weekly bar chart, monthly category breakdown, unified pace projection, and "What-If" simulators.
  - **Plan**: Multi-account balances (`Total des comptes`, `Solde du cycle`, `Épargne du cycle`), **Simulateur de Crédit**, category envelopes, recurring bills, savings goals, and Moroccan financial tools (*Daret*, *Sellef*, seasonal envelopes).
  - **Activité**: Friendly date grouping, swipe actions, search/filtering, and encrypted backup/export tools.

### Fast Capture & Smart Detection
- **5-Second Expense Entry**: Large numeric keypad, time-aware one-tap shortcuts, top recent category chips, and automatic category learning from past descriptions.
- **Multilingual Voice Capture**: Walkie-talkie voice entry supporting Darija, Arabic, French, and English keywords (`qahwa`, `gheda`, `echa`, `dwa`, `khobz`, `طاكسي`, etc.) plus spoken budget queries.
- **Bank Notification Detection**: Privacy-preserving on-device parsing for Moroccan bank SMS/app notifications (card purchases, ATM withdrawals, incoming/outgoing transfers) with OTP/3DS filtering and user review.
- **Home-Screen Widgets & Quick Capture**: Glance widgets and quick-capture notification actions synced automatically with your pay cycle.

### Moroccan Credit Simulator (*Simulateur de Crédit* — BCP Rules)
Accessible from both the **Dashboard Header** and the **Plan** tab, isolated in package `com.masroufi.credit`:
- **Moroccan Banking Presets**:
  - **Consommation**: `7.5%` HT default, up to `84` months (`120` months for civil servants or private-sector net income ≥ `30 000 MAD`), `500 000 MAD` standard ceiling.
  - **Auto (Neuve)**: `6.0%` HT default, up to `60` months.
  - **Immobilier**: `4.5%` HT default, up to `300` months.
- **Tiered Debt-to-Income (DTI) Ceilings**:
  - Without mortgage: `45%` (net income ≤ `15 000 MAD`) or `50%` (`> 15 000 MAD`).
  - With mortgage (*Immobilier* or existing mortgage): `50%` standard, `55%` (`35 000–60 000 MAD`), or `60%` (`> 60 000 MAD`).
- **HT + 10% Moroccan TVA & Amortization**: Calculates constant TTC installments with exact `HT` / `TVA 10%` breakdown, safe `0%` rate handling, optional `0–3` month partial grace period (*différé*), monthly ADI insurance, and a flat month-by-month amortization table.
- **Regulatory Alerts**: Flags over-indebtedness, borrower age `> 70` at final installment, Consommation ceiling `> 500 000 MAD`, net income below indicative Moroccan SMIG (`3 045 MAD`), and mandatory ADI insurance above `10 000 MAD`.
- **Masroufi Budget Integration**: Auto-prefills net income from your Masroufi salary and existing monthly debt from saved credits, persists scenarios in Firestore (`users/{uid}/credits/{creditId}`), and optionally creates a linked recurring monthly bill in one tap.

### Privacy, Security & Sync
- **Authentication & Local Mode**: Google Sign-In via Jetpack `CredentialManager` with Cloud Firestore sync (zero-trust owner-only security rules + Firebase App Check), or offline Local Device Mode.
- **On-Device Security**: One-tap amount masking (`••••`), optional `FLAG_SECURE` screen protection, and 6–8 digit PBKDF2-HMAC-SHA256 App Lock with biometric unlock and progressive lockout.
- **Encrypted Backups & Safe Exports**: Password-protected **AES-256-GCM** JSON backups with preview/rollback protection, plus formula-injection-neutralized CSV export.

---

## Architecture & Tech Stack

- **Language & UI**: Kotlin, Jetpack Compose (Material 3), Edge-to-Edge, Adaptive Light/Dark/System themes, bundled `Plus Jakarta Sans` & `Readex Pro` fonts.
- **Architecture**: MVVM with `ViewModel`, `StateFlow`, and Kotlin Coroutines/Flow.
  - Core app: `com.example` (`data`, `domain`, `ui`, `notifications`, `security`, `voice`, `widget`)
  - Isolated Credit Simulator module: `com.masroufi.credit` (`domain`, `data`, `presentation`)
- **Backend & Persistence**: Firebase Auth, Cloud Firestore (named database + zero-trust rules), Firebase App Check (`PlayIntegrity` in release), Android Keystore / Encrypted SharedPreferences, and WorkManager.

---

## Build, Test & Release

### Local Development
> Note: Use `gradle` directly (the Gradle wrapper is not required in the build container).

```bash
# Compile debug APK
gradle :app:assembleDebug

# Run unit & Robolectric test suites
gradle :app:testDebugUnitTest

# Run Credit Simulator math & ViewModel tests only
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
Signed, R8-minified production builds (`AAB` & `APK`) are automated via `.github/workflows/release-production.yml`:
1. Configure secrets in **Repository Settings → Environments → `production`**:
   - `RELEASE_KEYSTORE_BASE64`
   - `STORE_PASSWORD`
   - `KEY_PASSWORD`
   - `GOOGLE_SERVICES_JSON`
2. Trigger via a version tag push (e.g., `v1.1.2`) or manual `workflow_dispatch`.
3. Release APKs are published to GitHub Releases while signed AABs are uploaded as private workflow artifacts for Google Play.
