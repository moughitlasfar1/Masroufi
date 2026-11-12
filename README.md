# Masroufi

Masroufi is a fast, distraction-free personal finance and pay-cycle budgeting app tailored for everyday use in Morocco (`FR`, `AR`, `Darija`, `EN`).

## Key Features
- **Pay-Cycle Safe-to-Spend (`Reste à dépenser aujourd'hui`)**: Calculates daily spending allowance around custom salary days (`1..28`), accounting for committed bills and rollover envelopes.
- **Fast 5-Second Expense Entry**: Large centered numeric keypad, top 3 recent category chips, Darija/Arabic/French/English voice & keyword recognition (`qahwa`, `gheda`, `echa`, `dwa`, `khobz`, `طاكسي`), and automatic category learning from past descriptions.
- **4 Focused Tabs**:
  - **Accueil**: Safe-to-spend hero card (tap to expand streak, spent vs budget, and month-end forecast) and the 3 most recent transactions.
  - **Analyses**: Weekly bar chart and monthly category breakdown with unified pace projection and collapsible advanced simulators (`Plus`).
  - **Plan**: Accounts & cycle summary (`Total des comptes`, `Solde du cycle`, `Épargne du cycle`), category envelopes, and collapsible Moroccan financial tools (`Daret`, `Sellef`, `Objectifs & Factures`).
  - **Activité**: Friendly date headers, one-line transaction rows, swipe gestures, and collapsible search/filters.
- **Privacy & Security**: One-tap amount masking (`••••`), 6–8 digit PBKDF2-HMAC-SHA256 App Lock with biometric unlock, and AES-256-GCM encrypted JSON backups.

## Build & Test
- Compile debug APK: `gradle assembleDebug`
- Run unit & Robolectric tests: `gradle :app:testDebugUnitTest`
