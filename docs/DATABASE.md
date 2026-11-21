# Masroufi — Database Architecture & Migration Policy

## 1. Local Database Overview (`MasroufiDatabase`)

- **Engine**: Jetpack Room `2.7.0` (via KSP) backed by SQLCipher `4.20.0` (`SupportOpenHelperFactory`) and `androidx.sqlite:sqlite:2.7.0`.
- **Database File**: `masroufi_encrypted.db`
- **Current Schema Version**: `1`
- **Schema Export**: Enabled (`exportSchema = true`) via KSP argument `room.schemaLocation = "$projectDir/schemas"`. Exported schema snapshots live under `app/schemas/com.example.data.local.MasroufiDatabase/`.

## 2. Schema Version 1 Tables

| Table | Primary Key | Key Indexes | Purpose |
|---|---|---|---|
| `user_profiles` | `userId` | — | Budget base, pay-cycle day (`1..28`), rollover flag, language (`fr`/`ar`/`darija`/`en`), theme mode. |
| `accounts` | `id` | `userId` | Multi-account ledger (`cash`, `bank`, `wallet`), `currency` (default `"MAD"`), `initialBalance`, `isArchived`, `sortOrder`. |
| `transactions` | `id` | `userId`, `(userId, isoDate)`, `accountId`, `envelopeKey` | Expenses, incomes, and internal transfers (`accountId` → `toAccountId`), `isoDate`, `hourOfDay`. |
| `envelopes` | `id` | `userId` | Budget category envelopes with 4-language names, percentage, cap flag, and color. |
| `financial_items` | `id` | `userId`, `(userId, itemType)` | Recurring bills, savings goals, debts (*Slef*), *Daret* circles, seasonal envelopes, quick presets, and customization prefs. |
| `saved_credits` | `id` | `userId`, `(userId, createdAtEpochMs)` | Saved Moroccan / BCP Credit Simulator scenarios. |
| `import_metadata` | `userId` | — | Tracks one-time Firestore → Room import completion, `.masroufi` backup SHA-256 checksum, and verified per-collection row counts. |

## 3. Non-Destructive Migration Rules

1. **Never** call `fallbackToDestructiveMigration()` in production code.
2. Every schema version increment (`vN → vN+1`) **must** ship with:
   - An explicit `Migration(N, N + 1)` registered in `MasroufiDatabaseProvider`.
   - The new exported schema JSON committed in `app/schemas/com.example.data.local.MasroufiDatabase/<N+1>.json`.
   - An automated Room `MigrationTest` verifying zero row loss and accurate default/backfill values.

## 4. Phase 1b Implementation (Firestore → Encrypted Room)

- **Repository Interface & Routing**:
  - `MasroufiDataSource` is extracted from `MasroufiRepository`; `MasroufiViewModel` depends strictly on `MasroufiDataSource` with zero behavioral changes.
  - `RoomMasroufiRepository` writes atomically to Room first and performs non-blocking best-effort write-through to Firestore (swallowing offline errors) until Phase 2 replaces it with opt-in encrypted sync.
  - `SwitchingMasroufiRepository` observes `DataSourceConfigStore.activeMode` (`flatMapLatest`) to flip reactively from `FIRESTORE_LEGACY` to `ROOM_PRIMARY` without recreating ViewModels, and honors the **Firestore fallback kill switch** (`settings_firestore_kill_switch`) immediately if fallback is needed.
- **One-Time Read-Only `Source.SERVER` Import (`FirestoreToRoomImporter`)**:
  1. Reads all 6 user collections (`profile`, `accounts`, `transactions`, `envelopes`, `items`, `credits`) strictly with `Source.SERVER` — never modifies or deletes Firestore documents.
  2. If offline or any server read fails, aborts immediately with a localized 4-language error (`fr`/`ar`/`darija`/`en`) and leaves Room untouched.
  3. Writes an AES-256-GCM encrypted `pre_import_<uid>.masroufi` backup to private app storage BEFORE writing any row to Room.
  4. Writes all collections into Room inside a single atomic `@Transaction` (`replaceAllUserData`).
  5. Verifies every per-collection row count independently against Room before writing `import_metadata` and flipping `DataSourceConfigStore` to `ROOM_PRIMARY`.
  6. Fully idempotent and resumable on retry.
