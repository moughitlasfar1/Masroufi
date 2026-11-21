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

## 4. Phase 1b Transition Plan (Firestore → Encrypted Room)

- In Phase 1a, `MasroufiDatabase` v1 is bootstrapped and verified alongside the existing Firestore repository.
- In Phase 1b:
  1. Extract a clean data-source interface from `MasroufiRepository` and place a Room-backed repository behind it so ViewModels remain unchanged.
  2. Perform a safe **one-time read-only Firestore → Room import** for existing signed-in users:
     - First generate and persist an encrypted `.masroufi` backup snapshot.
     - Read all 6 user collections (`profile`, `accounts`, `transactions`, `envelopes`, `items`, `credits`) into Room inside a single atomic `@Transaction`.
     - Verify per-collection row counts match 100% and record verification in `import_metadata`.
     - **Never** modify or delete any Firestore documents during import.
