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

test("Unauthenticated user: cannot read bookings", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("bookings").get());
});

test("Authenticated user: can create and read own booking", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bookingDoc = aliceDb.collection("users").doc(ALICE_UID).collection("bookings").doc("booking_1");
  await assertSucceeds(
    bookingDoc.set({
      clientName: "Sophia Miller",
      serviceName: "Balayage & Blowout",
      staffName: "Emma Stylist",
      date: "2026-10-25",
      timeSlot: "10:00 AM",
      durationMin: 90,
      price: 180.0,
      status: "CONFIRMED",
      createdAt: new Date(),
    })
  );

  await assertSucceeds(bookingDoc.get());
});

test("Cross-user isolation: Bob cannot read Alice's bookings", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).collection("bookings").doc("booking_secret").set({
      clientName: "Alice Client",
      serviceName: "Spa Treatment",
      staffName: "Stylist One",
      date: "2026-10-25",
      timeSlot: "11:00 AM",
      status: "CONFIRMED",
      createdAt: new Date(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("bookings").doc("booking_secret").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("bookings").get());
});

test("Invalid booking status: fails security validation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("bookings").doc("booking_bad").set({
      clientName: "Bad Client",
      serviceName: "Haircut",
      staffName: "Staff",
      date: "2026-10-25",
      timeSlot: "02:00 PM",
      status: "INVALID_STATUS",
      createdAt: new Date(),
    })
  );
});
