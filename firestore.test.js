const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write user profile or transactions", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("transactions").get());
});

test("Authenticated user can create and read own profile and transactions, but not another user's", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 10_000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      baseBudget: 6400,
      payCycleDay: 25,
      rolloverEnabled: true,
      language: "fr",
      isDarkTheme: true,
      seeded: true,
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_1").set({
      userId: ALICE_UID,
      type: "expense",
      amount: 20,
      category: "Café & Sorties",
      envelopeKey: "cafe",
      accountId: "acc_cash",
      toAccountId: "",
      desc: "Café & garo",
      isoDate: "2026-10-30",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Cross-user read rejected
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_1").get());

  // Ghost field rejected (Shadow Update test)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_2").set({
      userId: ALICE_UID,
      type: "expense",
      amount: 20,
      category: "Café & Sorties",
      envelopeKey: "cafe",
      accountId: "acc_cash",
      toAccountId: "",
      desc: "Café",
      isoDate: "2026-10-30",
      isVerifiedGhostField: true,
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("SEC-08: payCycleDay > 28, future timestamps, mutated createdAt on update, and invalid transfer toAccountId are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const past = new Date(Date.now() - 15_000);
  const laterPast = new Date(Date.now() - 5_000);
  const farFuture = new Date(Date.now() + 3600_000);

  // 1. payCycleDay = 31 (> 28) is rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      baseBudget: 6400,
      payCycleDay: 31,
      rolloverEnabled: true,
      language: "fr",
      isDarkTheme: true,
      seeded: true,
      createdAt: past,
      updatedAt: past,
    })
  );

  // 2. Future createdAt (> request.time) is rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      baseBudget: 6400,
      payCycleDay: 28,
      rolloverEnabled: true,
      language: "fr",
      isDarkTheme: true,
      seeded: true,
      createdAt: farFuture,
      updatedAt: past,
    })
  );

  // 3. Valid profile creation with payCycleDay = 28 succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      baseBudget: 6400,
      payCycleDay: 28,
      rolloverEnabled: true,
      language: "fr",
      isDarkTheme: true,
      seeded: true,
      createdAt: past,
      updatedAt: past,
    })
  );

  // 4. Updating profile while mutating createdAt is rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      baseBudget: 7000,
      payCycleDay: 28,
      rolloverEnabled: true,
      language: "fr",
      isDarkTheme: true,
      seeded: true,
      createdAt: laterPast,
      updatedAt: laterPast,
    })
  );

  // 5. Updating profile while preserving createdAt succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      baseBudget: 7000,
      payCycleDay: 28,
      rolloverEnabled: true,
      language: "fr",
      isDarkTheme: true,
      seeded: true,
      createdAt: past,
      updatedAt: laterPast,
    })
  );

  // 6. Transfer with empty toAccountId or toAccountId == accountId is rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_bad_transfer_1").set({
      userId: ALICE_UID,
      type: "transfer",
      amount: 100,
      category: "Transfert",
      envelopeKey: "autre",
      accountId: "acc_cash",
      toAccountId: "",
      desc: "Transfert",
      isoDate: "2026-10-30",
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_bad_transfer_2").set({
      userId: ALICE_UID,
      type: "transfer",
      amount: 100,
      category: "Transfert",
      envelopeKey: "autre",
      accountId: "acc_cash",
      toAccountId: "acc_cash",
      desc: "Transfert",
      isoDate: "2026-10-30",
      createdAt: past,
      updatedAt: past,
    })
  );

  // 7. Expense with non-empty toAccountId is rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_bad_expense").set({
      userId: ALICE_UID,
      type: "expense",
      amount: 50,
      category: "Café",
      envelopeKey: "cafe",
      accountId: "acc_cash",
      toAccountId: "acc_bank",
      desc: "Café",
      isoDate: "2026-10-30",
      createdAt: past,
      updatedAt: past,
    })
  );

  // 8. Valid transfer with distinct toAccountId succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_good_transfer").set({
      userId: ALICE_UID,
      type: "transfer",
      amount: 300,
      category: "Transfert",
      envelopeKey: "autre",
      accountId: "acc_bank",
      toAccountId: "acc_cash",
      desc: "Retrait GAB",
      isoDate: "2026-10-30",
      createdAt: past,
      updatedAt: past,
    })
  );
});
