# Masroufi — Modern Banking UX & One-Handed Flow Overhaul

Upgrade **Masroufi** with fluid, thumb-friendly banking interactions—including horizontal swipe navigation across all five tabs, a one-tap Home quick-action bar, an expandable speed-dial Add menu, a persistent privacy eye toggle (`•••• DH`), swipe-to-edit/delete transaction gestures with Undo, and dual inline/studio Home dashboard customization—while preserving Masroufi's Moroccan Dirham identity, four-language support (FR, Darija, AR, EN with RTL/LTR), and real-time cloud synchronization.

## User Review & Critical Decisions

> [!IMPORTANT]
> All three product and interaction preferences from our clarification step have been locked into this plan:

- **Confirmed Decision 1 — Smarter Floating Add Button**: Tapping the floating `+` button opens an animated **Speed-Dial Fan Menu** offering direct one-tap access to **Expense**, **Income**, **Transfer**, and **Quick Preset**, complete with a dimmed backdrop scrim and tactile haptic feedback.
- **Confirmed Decision 2 — Privacy Eye Button Persistence**: The **Hide/Show Money** eye toggle in the top bar and Hero card masks every currency figure across the app (`•••• DH`) and **remembers your last chosen state across app launches** so balances stay hidden when opening Masroufi in public.
- **Confirmed Decision 3 — Customizable Home Dashboard Controls**: Users can **rearrange and hide/show Home cards** both directly via an inline **"Edit Home"** mode on the Home screen and inside the **Customization Studio**, with card order and visibility persisted automatically.

---

## 1. Overview & Core Concept

- **What It Does**: Transforms daily expense and budget management in Masroufi into a fast, one-handed banking experience where every core action—switching tabs, logging an expense or income, moving money between accounts, hiding balances in public, editing or deleting a transaction, and tailoring the Home layout—takes a single swipe or tap without hunting through nested menus.
- **Target Audience / Persona**: Moroccan and international users managing personal budgets, envelopes, Daret tontines, bills, and multi-account balances (`Cash`, `Bank`, `Mobile Wallet`) on the go, often with one hand or in public spaces.
- **Key Value**:
  - **Zero-friction navigation**: Swipe left or right anywhere on the main canvas to move across the 5 tabs while keeping scroll position intact.
  - **Instant entry**: Log expenses, income, transfers, or recurring presets in one tap from the Home quick-action bar or the floating speed-dial fan.
  - **Public privacy & safety**: Mask all financial amounts with one tap (`•••• DH`), and safely swipe transactions with a guaranteed **Undo** snackbar before deletion.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Swipe Between Main Tabs**:
   - Swiping left advances to the next tab (`Vue` → `Semaine` → `Mois` → `Catégories` → `Historique`), and swiping right returns to the previous tab, automatically respecting RTL direction when Arabic or Darija is active.
   - The bottom navigation bar stays anchored, animates its active indicator in sync with the swipe, and supports normal single-tap switching.
   - Each tab preserves its vertical scroll position when switching back and forth, and Masroufi remembers the last visited tab when reopened.
2. **One-Tap Home Quick-Action Area**:
   - Directly below the Hero Balance Card on the Home screen sits a sleek 4-action banking bar:
     - **Expense** (*One tap to add*) — opens the Expense sheet with amount field focused.
     - **Income** (*One tap to add*) — opens the Income sheet ready for salary or side income.
     - **Transfer** (*Move between accounts*) — opens the account-to-account transfer sheet.
     - **Quick Preset** (*Repeat common expenses*) — opens a fast preset selector / one-tap preset launcher for coffee, transport, groceries, or custom presets.
3. **Speed-Dial Floating Add Menu**:
   - Tapping the primary floating `+` button rotates the icon smoothly into an `×`, dims the background with a subtle scrim, and fans out four labeled pill buttons (**Quick Preset**, **Transfer**, **Income**, **Expense**) right under the user's thumb.
4. **Hide/Show Financial Amounts (Privacy Eye Toggle)**:
   - An eye icon button (`Visibility` / `VisibilityOff`) in the top header and Hero card toggles privacy mode globally.
   - When active, all monetary figures across the Hero card, account balances, envelopes, charts, bills, and transaction lists render as `•••• DH` while keeping category names and progress bar shapes visible.
   - The privacy state is saved immediately to device preferences so reopening the app in a café or transit keeps amounts hidden.
5. **Swipe on Transactions with Undo**:
   - In transaction lists, **swiping right** reveals an Emerald edit background and opens the transaction editor sheet.
   - **Swiping left** reveals a Coral delete background, removes the transaction, and triggers a bottom **Snackbar with an "Undo" action** that restores the transaction immediately if tapped.
6. **Customizable Home Dashboard (Inline + Studio)**:
   - An **"Edit Home"** toggle pill on the Home screen lets users move dashboard cards up or down (`Accounts`, `Quick Presets`, `Weekly Rhythm`, `Monthly Review`, `Upcoming Bills`, `Smart Insights`) or toggle their visibility inline with instant visual feedback.
   - The same card order and visibility controls are mirrored inside the **Customization Studio**.
7. **Long-Press Navigation Shortcuts**:
   - Long-pressing any bottom navigation tab triggers a subtle haptic pulse and opens a compact shortcut menu tailored to that section (e.g., long-pressing **Home** offers *Add Expense / Income / Transfer*; long-pressing **Budget** offers *New Envelope / Bill / Daret / Goal*; long-pressing **History** offers *Search / Export CSV / Encrypted Backup*).

### Visual Identity & Theme

- **Aesthetic Direction**: Expressive Moroccan Fintech—combining high-contrast dark obsidian surfaces (`#08120E`) and crisp alabaster light surfaces (`#F6FAF7`) with rich gradient hero cards, pill badges, and responsive spring physics.
- **Color Palette & Mood**:
  - **Primary / Income / Positive**: Emerald (`#10B981`) & Mint (`#6EE7B7`), adaptable to user-selected palettes (*Emerald*, *Majorelle Blue*, *Amber*, *Amethyst*, *Terracotta*, *Ocean*).
  - **Expense / Alert / Delete Gesture**: Warm Coral (`#FF6B6B`).
  - **Transfer / Info**: Sky Blue (`#38BDF8`).
  - **Presets / Rollover / Highlights**: Moroccan Amber Gold (`#F59E0B`).
- **Typography & Hierarchy**:
  - Tabular numerals (`tnum`) for all currency displays and privacy masks (`•••• DH`) so layout height never jumps when toggling privacy mode.
  - Bold expressive hero figures paired with compact, scannable pills for quick actions and swipe hints.
- **Interactive Feedback & Motion**:
  - Light haptic ticks (`HapticFeedbackType.TextHandleMove` / `LongPress`) when crossing tab boundaries, toggling the privacy eye, expanding the speed-dial FAB, or triggering a transaction swipe threshold.
  - Predictive back navigation: pressing Back closes an open speed-dial menu or inline edit mode first, and from any secondary tab returns smoothly to the Home (`Vue`) tab before exiting.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1 — Gesture Disambiguation Between Tab Pager and Inner Horizontal Controls**:
  - *Chosen Approach*: Use a synchronized `HorizontalPager` for the 5 main screens while isolating horizontal pointer consumption inside horizontal scroll strips (account cards, preset pills, filter chips) and transaction swipe rows.
  - *Why*: Users get effortless edge-to-edge tab swiping without accidentally changing tabs while scrolling through accounts, filter chips, or swiping a transaction row to edit/delete.
  - *Alternatives Considered*: Restricting tab swipes to the top app bar only—rejected because it defeats one-handed thumb navigation.
- **Decision 2 — Persistent Local Preferences + Cloud Customization Sync**:
  - *Chosen Approach*: Store instant device-level UX states (`isPrivacyMaskEnabled`, `lastActiveTab`, and `dashboardCardOrder`) in encrypted/hardened local preferences for zero-latency startup, while also encoding dashboard card order and visibility in `CustomizationPrefs` so layout preferences stay synchronized with Firestore.
  - *Why*: Guarantees the privacy mask (`•••• DH`) and last selected tab are applied on the very first frame when launching the app, even offline.
  - *Alternatives Considered*: Waiting for Firestore snapshot load before applying the privacy mask—rejected because balances would briefly flash on screen during startup.
- **Decision 3 — Non-Destructive Swipe-to-Edit and Undoable Swipe-to-Delete**:
  - *Chosen Approach*: Swiping right snaps back after launching the edit modal (non-destructive), while swiping left caches the deleted `TransactionItem` in `MasroufiViewModel` and presents a Snackbar with an **Undo** action.
  - *Why*: Prevents accidental data loss during fast one-handed scrolling while eliminating confirmation dialogs that slow down cleanup.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                   MasroufiRoot & Top Bar (Privacy Eye Toggle)               │
│  • Global Privacy Mask State (•••• DH)   • Quick Settings & Theme Controls  │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│              Synchronized HorizontalPager (5 Main Banking Tabs)             │
│  ┌───────────┐  ┌───────────┐  ┌───────────┐  ┌───────────┐  ┌───────────┐  │
│  │ 1. Home   │◄─► 2. Week   │◄─► 3. Month  │◄─► 4. Budget │◄─► 5.History │  │
│  │   (Vue)   │  │ (Semaine) │  │  (Mois)   │  │(Catégorie)│  │(Historique│  │
│  └─────┬─────┘  └───────────┘  └───────────┘  └───────────┘  └─────┬─────┘  │
└────────┼───────────────────────────────────────────────────────────┼────────┘
         │                                                           │
         ▼                                                           ▼
┌──────────────────────────────────────┐   ┌──────────────────────────────────┐
│    Customizable Home Dashboard       │   │   Swipeable Transaction List     │
│  • Hero Card + Privacy Eye Pill      │   │  • Swipe Right ──► Edit Modal    │
│  • 4-Button Quick-Action Bar:        │   │  • Swipe Left  ──► Delete +      │
│    [Expense][Income][Transfer][Preset│   │                    Undo Snackbar │
│  • Reorderable Cards (Inline + Studio│   │  • Actionable Empty State CTA    │
└──────────────────────────────────────┘   └──────────────────────────────────┘
         ▲                                                           ▲
         └─────────────────────────────┬─────────────────────────────┘
                                       │
┌──────────────────────────────────────┴──────────────────────────────────────┐
│       Speed-Dial Floating Action Menu & Long-Press Bottom Nav Bar           │
│  • Speed-Dial Fan: Expense | Income | Transfer | Quick Preset               │
│  • Bottom Nav: Tap to animate page • Long-Press for contextual shortcut pop │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│            MasroufiViewModel + AppSecurityManager + Firestore               │
│  • Persists: hideAmounts, lastActiveTab, dashboardCardOrder & visibility    │
│  • Undo Buffer: lastDeletedTransaction + restore on Snackbar action         │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Data Model & State

- **Privacy & Navigation Preferences**:
  - `hideAmounts: Boolean`: Stored in local preferences and exposed in `DashboardState` so a single formatting helper (`formatCurrencyOrMask(amount, currencySymbol, hideAmounts)`) masks or reveals every monetary value consistently across all five tabs.
  - `lastActiveTab: String`: Persisted whenever the user switches tabs via swipe or tap (`"vue"`, `"semaine"`, `"mois"`, `"categories"`, `"historique"`) and restored on launch.
- **Customizable Home Dashboard Schema (`CustomizationPrefs`)**:
  - Extends `CustomizationPrefs` with an ordered list of Home section identifiers (`dashboardOrder: List<String>`, defaulting to `["accounts", "presets", "week_summary", "monthly_review", "bills", "insights"]`) alongside existing visibility flags (`showSafeToSpend`, `showAccountsCard`, `showQuickPresetsCard`, `showWeekSummaryRow`, `showMonthlyReviewCard`, `showBillsCard`).
  - Serializes cleanly into both local preferences and the existing Firestore `custom_prefs` document without breaking backward compatibility.

### Interactive Component & State Mapping

- **`HorizontalPager` & Bottom Navigation Sync**:
  - `pagerState.currentPage` drives the selected bottom navigation item; tapping a bottom navigation item launches `coroutineScope.launch { pagerState.animateScrollToPage(index) }` with a light haptic tick.
  - Each tab retains its own `LazyListState` so vertical scroll offset is preserved across swipes.
- **Home Quick-Action Bar & Speed-Dial FAB**:
  - **Expense / Income / Transfer** buttons invoke `openTransactionEditor(defaultType = ...)` directly.
  - **Quick Preset** opens a bottom sheet / quick picker showing existing one-tap presets (with a button to create a new preset) so users can trigger any recurring expense from any tab or from the Home quick bar.
- **Swipeable Transaction Row (`SwipeToDismissBox` / Horizontal Drag)**:
  - Start-to-End swipe (Right in LTR) reaches threshold → triggers haptic feedback, opens `TransactionEditorSheet(tx)`, and resets row offset without removing the item.
  - End-to-Start swipe (Left in LTR) reaches threshold → triggers haptic feedback, calls `viewModel.deleteTransactionWithUndo(tx)`, and displays the Undo `Snackbar`.
- **Long-Press Navigation Shortcuts**:
  - Bottom navigation items detect long-press gestures to display a contextual shortcut sheet/menu (`Add Expense`, `Add Income`, `Transfer`, `New Envelope`, `New Bill`, `New Daret`, `Export CSV/Backup`) for zero-menu jumping.
