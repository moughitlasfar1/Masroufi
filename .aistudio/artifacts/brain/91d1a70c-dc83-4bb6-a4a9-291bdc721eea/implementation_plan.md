# Masroufi 2.0 — Personal Finance, Budget Envelopes & Daret Tracker for Android

Masroufi 2.0 is a native Android personal finance application tailored for Moroccan households and everyday spending in Dirhams (DH). It upgrades the Masroufi 2.0 baseline with editable transactions and undo recovery, customizable budget envelopes with month-over-month rollover, custom salary pay-cycle dates, multi-account balances (Cash, Bank, Mobile Wallet) with transfers, Debt & Moroccan Daret (tontine) tracking, smart "Safe to Spend Today" calculations, a "What-If" savings simulator, seasonal event planners (Ramadan, Eid, Rentrée, Vignette/Assurance), and real-time cloud sync via Firebase Firestore & Google Sign-In alongside local JSON/CSV backup.

### User Review & Critical Decisions

> [!IMPORTANT]
> The following product and architectural choices have been aligned based on your selections and will guide the implementation:

- **Confirmed Decision 1 — Comprehensive Feature Scope**: We will build the complete Masroufi 2.0 baseline plus all "Get the basics right", "Know what you really have", and selected "Smarter insights" & "Safety and polish" features:
  - **Editable Transactions & Undo**: Tap or swipe any transaction to edit its amount, category, account, description, or date, or delete it with an instant Snackbar **Undo** action.
  - **Custom Categories & Envelopes with Rollover**: Add/edit custom categories and adjust envelope percentage splits (defaulting to the classic 35% Transport / 26.5% Fixed Bills / 18.5% Café & Outings / 20% Savings) with an optional toggle to carry over unused envelope balances into the next pay cycle.
  - **Pay-Cycle Months**: Configure your salary payday (e.g., the 25th of each month so a budget cycle runs from the 25th to the 24th of the next month).
  - **Multi-Account Balances & Transfers**: Track separate balances across Cash (*Espèces*), Bank (*Banque*), and Mobile Wallet (*Wafacash / CIH Mobile / Inwi Money*), assign transactions to specific accounts, and record inter-account transfers.
  - **Search, Filters & Quick-Add Presets**: Filter history by category, account, type, amount range, or text search; one-tap Quick-Add chips on the home screen for frequent daily purchases (e.g., Café 15 DH, Taxi 20 DH, Khobz/Bread 5 DH, Garo 20 DH) plus a static Android launcher app-icon shortcut to jump straight into adding an expense.
  - **Safe-to-Spend Today**: Real-time daily allowance calculated as `(Remaining Budget + Rollover - Unpaid Recurring Bills for Cycle) ÷ Remaining Days in Pay Cycle`.
  - **Debts, Loans & Moroccan Daret Tracker**: Dedicated tracker for money owed to you (*Crédits*), money you owe (*Dettes*) with partial payment logging, and a **Daret** (rotating savings circle) manager tracking members, monthly contribution, your payout turn, paid rounds, and payout date.
  - **Smarter Insights & Seasonal Planners**: End-of-cycle *Bilan du mois* review card (highlighting best and worst categories), an interactive *"What-If" Simulator* (e.g., "If I cut Café & Sorties by 30%, I save X DH by year-end"), and Seasonal Planners for Ramadan, Eid al-Adha, Back-to-School (*Rentrée*), and Car Tax/Insurance (*Vignette & Assurance*).
  - **Multilingual & Theme Polish**: Full 4-language switcher (**Français**, **العربية**, **دارجة مغربية**, **English**) with automatic RTL/LTR layout mirroring, a **Dark / Light Theme** toggle, and an interactive **Category Donut Chart** alongside the 7-day high-contrast bar chart.
- **Confirmed Decision 2 — Cloud Sync with Google Sign-In + Local Export**: All user data is authenticated with **Google Sign-In** (via Jetpack `CredentialManager`) and synced in real time with **Firebase Firestore** (with offline caching enabled), complemented by one-tap **JSON & CSV export/import** via the system share sheet and clipboard.

---

### 1. Overview & Core Concept

- **What It Does**: Masroufi gives users an immediate, accurate answer to *"How much do I actually have left to spend today?"* and *"Where is my money going (`Fach Katemchi Lflos`)?"* across their cash, bank, and wallet accounts, respecting their actual salary payday and Moroccan financial habits (such as Daret circles, family credits, and seasonal expenses).
- **Target Audience / Persona**: Moroccan professionals, students, and families who budget in Dirhams (DH), switch effortlessly between French, Darija, Arabic, and English, and want banking-grade clarity without spreadsheet friction.
- **Key Value**: Combines pay-cycle-aware envelope budgeting, multi-account net worth, upcoming bill reservations, and cultural financial tools (Daret, Ramadan/Eid planners, quick-add street expenses) into a fast, expressive Material 3 interface.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Sign-In & Initial Seed**: On launch, users sign in with their Google Account via a clean Material 3 welcome screen. New accounts are automatically initialized with the Masroufi default envelopes (35% / 26.5% / 18.5% / 20%), default accounts (Cash, Bank, Mobile Wallet), quick-add presets, and starter transactions so the dashboard is immediately rich and interactive.
  2. **Home Overview (`Accueil`)**:
     - **Top Bar**: Brand logo with `DH` badge, Language Pill (`FR` / `AR` / `دارجة` / `EN`), Dark/Light theme toggle, and Settings button (salary budget, payday cycle day `1–31`, rollover toggle).
     - **Pay-Cycle Navigator**: Cycle switcher showing the exact date window (e.g., `25 Oct → 24 Nov 2026`) with previous/next cycle arrows.
     - **Multi-Account Strip**: Horizontal cards for *Cash*, *Banque*, and *Mobile Wallet* showing live balances and a `+ Transfert` action button.
     - **Hero Budget & Safe-to-Spend Card**: Expressive emerald gradient card displaying total cycle spending, remaining budget, progress bar, and a prominent **"Safe to Spend Today"** badge that subtracts unpaid recurring bills before dividing by remaining days in the cycle.
     - **Quick-Add Bar**: One-tap chips (`☕ Café 15 DH`, `🚕 Taxi 20 DH`, `🥖 Pain 5 DH`, `🚆 ONCF 50 DH`, plus customizable presets) that log an expense immediately and show a Snackbar with an **Annuler (Undo)** button.
     - **Monthly Review (*Bilan du mois*) & Advice Card**: Smart summary of savings rate, best/worst envelope performance, and upcoming recurring bills due within 7 days.
  3. **Weekly Analysis (`Semaine / Simana`)**:
     - 7-day Monday–Sunday breakdown for the active pay cycle, most expensive day highlight, active-day average, and a high-contrast 7-pillar bar chart with dashed DH reference grid lines.
  4. **Monthly Insights & Simulators (`Mois / Chehar`)**:
     - Cycle net balance, end-of-cycle spending projection, **"What-If" Savings Simulator** with interactive sliders (e.g., reduce *Café & Sorties* or *Transport* by 10%–50% to see projected 6-month and 12-month DH savings), and **Seasonal Planners** (Ramadan, Eid, Rentrée, Vignette) with target budgets and countdown progress.
  5. **Budget, Envelopes & Daret (`Budget / Fach Katemchi Lflos`)**:
     - Interactive **Category Donut Chart** with center total and legend percentages.
     - **Editable Envelopes**: Customize envelope names, percentage allocations (with a live 100% validation indicator), and optional **Rollover** badges showing unspent DH carried over from the previous cycle.
     - **Debts & Loans (*Crédits & Dettes*)**: Track who owes you (e.g., *Crédit mama*, *Crédit inaam*) and who you owe, with one-tap partial or full repayment recording.
     - **Moroccan Daret Tracker**: Manage rotating savings pools (total pool amount, monthly contribution, member count, your turn month, and one-tap monthly contribution logging).
     - **Savings Goals & Recurring Bills**: Create/contribute to goals and mark monthly fixed bills as paid.
  6. **History & Backup (`Historique`)**:
     - Search bar and multi-criteria filter chips (Category, Account, Expense/Income, Min/Max Amount).
     - **Tap to Edit / Swipe to Delete**: Tapping any transaction opens the pre-filled Edit Bottom Sheet (modify amount, category, account, date, or note). Deleting triggers a 5-second Snackbar with **Undo** (`Annuler`) to restore the transaction and revert the account balance.
     - **Export & Backup Card**: Download/Share JSON, Copy JSON to clipboard, Export CSV, and Restore from JSON.

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: **Material 3 Expressive Emerald Luxury** — inspired by the Masroufi 2.0 design language, pairing deep forest-emerald surfaces in Dark Mode and crisp mint-ivory surfaces in Light Mode with asymmetric hero card corners (`40.dp` top/right, `14.dp` bottom-start) and glossy tonal elevation.
  - *Color Palette & Mood*:
    - **Dark Theme**: Surface `#0D1410`, Surface Container `#18211C`, Surface Container High `#212B25`, Primary Emerald `#6FDCA0`, Primary Container `#0F5132`, Accent Sky Blue `#7CC4F8`, Warning Amber `#FBBF24`, Expense Coral `#FFB4AB`.
    - **Light Theme**: Surface `#F4FBF6`, Surface Container `#E6F2EA`, Surface Container High `#DAEBE0`, Primary Emerald `#059669`, Primary Container `#B9F6D0`, On-Surface `#0D1F16`.
  - *Typography & Hierarchy*: Local Google Fonts bundled via `font-util` (`Plus Jakarta Sans` / `Montserrat` for bold geometric numerals and headers paired with `Inter` / `Noto Sans Arabic` support for clean multilingual FR/AR/Darija/EN legibility).
  - *Component Styling & Layout*: Edge-to-edge layout respecting `WindowInsets.safeDrawing` and `WindowInsets.navigationBars`, spring-animated bottom navigation pills, modal bottom sheets with drag handles, and adaptive `widthIn(max = 600.dp)` containers for tablet/foldable ergonomics.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Pay-Cycle Window Calculation Engine**
  - *Chosen Approach*: Represent every budget period as a `PayCycleWindow(startDate, endDate, label)` derived from the user's configured `payCycleDay` (`1..28`, or end-of-month clamping). If `payCycleDay = 25`, viewing "October 2026" resolves to `2026-10-25` through `2026-11-24`.
  - *Why*: Users who receive their salary on the 25th or 28th experience distorted budgets when apps hardcode `01` to `30/31`. Dynamic date-window filtering makes every metric (monthly spend, safe-to-spend today, envelope usage, rollover, and weekly comparisons) 100% accurate.
- **Decision 2: Cloud-First Persistence with Firestore + Local JSON/CSV Portability**
  - *Chosen Approach*: Store user configuration, transactions, accounts, envelopes, debts, Daret pools, goals, bills, and seasonal planners under `/users/{userId}` and subcollections in Firebase Firestore, authenticated via Google Sign-In (`CredentialManager`), while maintaining instant JSON/CSV export and restore.
  - *Why*: Ensures zero data loss if the user switches phones or reinstalls the app, works seamlessly offline via Firestore's local cache, and preserves full compatibility with Masroufi 2.0 JSON backups.
- **Decision 3: Unified Account Balance Computation**
  - *Chosen Approach*: Each account (Cash, Bank, Mobile Wallet, or custom) has an `initialBalance`, and its live balance is dynamically computed from its initial balance + incomes - expenses ± transfers linked to that account ID.
  - *Why*: Guarantees that editing, deleting, or undoing a transaction automatically keeps account balances in sync without drift or race conditions.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram**:

```
┌────────────────────────────────────────────────────────────────────────────┐
│                        MainActivity (Edge-to-Edge)                         │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │               AuthGateComposable (CredentialManager)                 │  │
│  │   [Unauthenticated] ──► WelcomeSignInScreen (Google Sign-In)         │  │
│  │   [Authenticated]   ──► MasroufiAppScaffold (RTL/LTR + Theme State)  │  │
│  └──────────────────────────────────┬───────────────────────────────────┘  │
└─────────────────────────────────────┼──────────────────────────────────────┘
                                      ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                   MasroufiViewModel (StateFlow + PayCycle)                 │
│  • PayCycleEngine (startDay 1..31, Safe-To-Spend, Rollover Calculation)    │
│  • UndoBuffer (LastDeletedTransaction + Snackbar Restoration)              │
│  • Localization & Theme State (FR / AR / Darija / EN + Dark / Light)       │
└──────┬──────────────┬──────────────┬───────────────┬───────────────┬───────┘
       ▼              ▼              ▼               ▼               ▼
┌────────────┐ ┌────────────┐ ┌─────────────┐ ┌─────────────┐ ┌──────────────┐
│  Accueil   │ │  Semaine   │ │    Mois     │ │   Budget    │ │  Historique  │
│  Overview  │ │  7-Day Bar │ │  What-If &  │ │ Donut, Env, │ │ Search, Edit │
│  Accounts, │ │  Chart &   │ │  Seasonal   │ │ Daret, Debt │ │ Swipe Delete │
│ Quick-Add  │ │  Metrics   │ │  Planners   │ │ Goals, Bills│ │ JSON / CSV   │
└──────┬─────┘ └──────┬─────┘ └──────┬──────┘ └──────┬──────┘ └──────┬───────┘
       └──────────────┴──────────────┼───────────────┴───────────────┘
                                     ▼
┌────────────────────────────────────────────────────────────────────────────┐
│             MasroufiRepository (Custom DB ID: firestore_database_id)       │
│  • /users/{uid} (Profile, baseBudget, payCycleDay, rolloverEnabled, theme) │
│  • /users/{uid}/transactions/{txId} (type, amount, categoryId, accountId)  │
│  • /users/{uid}/accounts/{accId}    (name, type, initialBalance, icon)     │
│  • /users/{uid}/envelopes/{envId}   (name, pct, color, keywords)           │
│  • /users/{uid}/obligations/{obId}  (Bill, Goal, Debt/Loan, Daret, Season) │
└────────────────────────────────────────────────────────────────────────────┘
```

- **Data Model & State**:
  - `UserProfile` (`/users/{uid}`): `userId`, `baseBudget` (Double, default `6400.0`), `payCycleDay` (Int, default `1`, configurable `1..31`), `rolloverEnabled` (Boolean), `language` (`fr` | `ar` | `darija` | `en`), `isDarkTheme` (Boolean), `quickPresetsJson` (list of quick-add items), `updatedAt` (`@ServerTimestamp`).
  - `TransactionItem` (`/users/{uid}/transactions/{id}`): `id`, `userId`, `type` (`expense` | `income` | `transfer`), `amount` (Double), `category` (String), `envelopeKey` (String), `accountId` (String), `toAccountId` (String, for transfers), `desc` (String), `isoDate` (`YYYY-MM-DD`), `createdAt`, `updatedAt`.
  - `AccountItem` (`/users/{uid}/accounts/{id}`): `id`, `userId`, `name` (String), `kind` (`cash` | `bank` | `wallet`), `initialBalance` (Double), `colorHex` (String), `createdAt`, `updatedAt`.
  - `EnvelopeConfig` (`/users/{uid}/envelopes/{id}`): `id`, `userId`, `key` (String), `nameFr`, `nameAr`, `nameDarija`, `nameEn`, `percentage` (Double), `hasCap` (Boolean), `colorHex` (String), `createdAt`, `updatedAt`.
  - `FinancialItem` (`/users/{uid}/items/{id}`): Unified collection for structured trackers distinguished by `itemType`:
    - `goal`: Savings goal (`name`, `targetAmount`, `savedAmount`, `deadlineIso`)
    - `bill`: Recurring monthly bill (`name`, `amount`, `dueDay`, `paidCycles` list)
    - `debt`: Debt or Loan (`name`, `personName`, `amount`, `paidAmount`, `direction`: `lent` | `borrowed`, `dueDateIso`)
    - `daret`: Moroccan Daret circle (`name`, `monthlyContribution`, `totalMembers`, `myTurnNumber`, `paidMonthsCount`, `payoutDateIso`)
    - `seasonal`: Seasonal event planner (`name`, `eventType`: `ramadan` | `eid` | `rentree` | `vignette`, `targetAmount`, `savedAmount`, `eventDateIso`)

- **Interactive Component & State Mapping**:
  - **Add / Edit Transaction Bottom Sheet**: Supports Expense, Income, and Account-to-Account Transfer modes. Validates positive amounts, selects source (and destination) account, category/envelope, date picker, and note. In Edit mode, updates the existing Firestore document (`docRef.set(...)`) preserving `createdAt`.
  - **Delete with Snackbar Undo**: Removes the transaction from Firestore and caches the deleted `TransactionItem` in `ViewModel`. Tapping **Undo** on the Snackbar re-inserts the exact transaction document.
  - **Custom Envelopes & Pay-Cycle Editor**: Modal sheet allowing users to add/edit custom categories, adjust envelope percentage sliders/inputs with a live sum indicator, set their salary payday (`1–31`), and toggle envelope rollover.
  - **Search & Multi-Filter History**: Reactive `combine` flow filtering transactions by search query (description/category), selected category chip, selected account chip, and min/max DH amount.
  - **What-If Simulator**: Interactive percentage reduction sliders per envelope that immediately calculate projected savings over 1 month, 6 months, and 1 year without mutating stored transactions.
  - **Android App Shortcut**: Static `res/xml/shortcuts.xml` wired to `MainActivity` with an `intent.action` (`ACTION_QUICK_ADD_EXPENSE`) that opens the Add Expense sheet immediately on launch when triggered from the launcher icon long-press.
