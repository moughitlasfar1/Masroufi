# Masroufi — Unified Engineering Roadmap (Revised)

This roadmap incorporates all confirmed architectural revisions for upgrading **Masroufi** into an encrypted, offline-first personal finance app with optional cloud sync.

## Shared Rules (Active Across All Phases)
1. **Local-First Source of Truth**: Encrypted SQLCipher Room DB (`masroufi_encrypted.db`) is the local source of truth; Firebase Firestore remains an opt-in sync layer.
2. **Data Safety & Migrations**: Every Room schema bump includes an explicit `Migration` (never `fallbackToDestructiveMigration()` in production), an exported schema JSON in `app/schemas/`, and an automated Room migration test.
3. **Localization**: All 4 languages (`fr`, `ar` with RTL, `darija`, `en`) remain in Kotlin runtime tables (`Translations.kt`, `CreditStrings.kt`, and feature string providers) — **no Android XML locale string resources**. A key-parity unit test covering all 4 languages is enforced in Phase 10.
4. **Key Management**: Reuse `SecureStore` (`AndroidKeyStore` AES-256-GCM with per-key AAD) — never create duplicate Keystore wrapping keys.
5. **Build Configuration**: No Gradle `productFlavors` (AI Studio build constraint). Keep Firebase/GMS isolated behind interfaces in `src/main`.
6. **Delta-First Upgrades**: Audit existing Masroufi features (`AppLock`, `FLAG_SECURE`, backup rules, `AccountItem`, `FinancialItem`, dashboard section ordering, home widget) before modifying and implement only the missing delta.
7. **R8 Discipline**: Minimal SQLCipher R8 keep rules (`net.zetetic.database.**` and native methods only — no `SQLiteOpenHelper` keepclassmembers rule).

---

## Execution Phases

| Phase | Scope | Status |
|---|---|---|
| **1a** | Encrypted Local Room DB (`net.zetetic:sqlcipher-android:4.20.0@aar` + `androidx.sqlite:sqlite:2.7.0`), `MasroufiApplication` (`System.loadLibrary("sqlcipher")`), `DatabaseKeyManager` reusing `SecureStore`, on-disk header encryption check (`!= "SQLite format 3"`), Room v1 schema + exported JSON, `DatabaseRecoveryDialog`, minimal R8 rules, Logcat PII audit, `/docs/*`, unit tests & R8 check. | **Completed** |
| **1b** | Extracted `MasroufiDataSource` interface from `MasroufiRepository`; implemented `RoomMasroufiRepository` (Room-first with non-blocking best-effort Firestore write-through until Phase 2), `SwitchingMasroufiRepository` + Firestore fallback kill switch, strict `Source.SERVER` read-only idempotent/resumable `FirestoreToRoomImporter` (encrypted `.masroufi` backup first + per-collection count verification before flipping), and post-import explicit confirmation guard in `DatabaseRecoveryDialog`. | **Completed** |
| **2** | Isolate Firebase/GMS behind `CloudSyncRepository` and `VoiceIntegrationBridge` interfaces (no Gradle `productFlavors`); make cloud sync strictly opt-in with client-side AES-256-GCM field encryption (PBKDF2 key derived from user sync passphrase); keep `.masroufi` encrypted local backup/restore available offline. | Next |
| **3** | Audit existing `AccountItem` & transfers; upgrade Room `accounts` + `transactions` (`currency`, `isArchived`, `sortOrder`, `profileId`, atomic paired transfers with `transferPeerId`), Net Worth hook, account picker in Keypad & Moonshine voice sheet, and Room `MigrationTest`. | Planned |
| **4** | `TagEntity` + `TransactionTagCrossRef`, `SearchNormalizer` (French accents + Arabic/Darija normalization), Room `@Fts4` (`TransactionSearchFts`) kept in sync via `@Transaction` + `rebuildSearchIndex()`, debounced Search UI with filters & highlighting. | Planned |
| **5** | Audit existing `FinancialItem(itemType="bill")`; introduce `PlannedPaymentEntity`, pure recurrence engine (month-end Feb 28/29 safe), idempotent WorkManager auto-post & reminders, overdue/upcoming UI, migrate existing bills into Room. | Planned |
| **6** | Audit existing `FinancialItem(itemType="debt")`; introduce `LoanEntity` & `LoanPaymentEntity` (Lent/Borrowed / Slef), overpayment guard, atomic account balance updates, Net Worth (`Assets - Liabilities`) split, migrate existing debts into Room. | Planned |
| **7** | Audit existing `CustomizationPrefs.dashboardOrder`; upgrade Home to `DashboardCardSpec` registry with DataStore versioned JSON layout, drag-and-drop Edit Mode, Add Card bottom sheet, independent `StateFlow` card providers. | Planned |
| **8** | Audit existing `MasroufiAppWidget`; add Jetpack Glance home-screen widgets reusing Phase 7 card data providers + instant Moonshine voice capture action. | Planned |
| **9** | Embedded Ktor LAN web companion server over local Wi-Fi with PIN/QR authentication for desktop inspection, editing, and export. | Planned |
| **10** | Final security & release audit, 4-language Kotlin translation key-parity unit test, minified release R8 verification, and documentation sync. | Planned |
