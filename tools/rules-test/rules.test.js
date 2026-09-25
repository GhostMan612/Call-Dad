// Firestore security-rules tests against the local emulator (synthetic UIDs only).
// Run from this folder:
//   npm install
//   npx firebase emulators:exec --only firestore --project demo-calldad "node --test"
const test = require("node:test");
const fs = require("node:fs");
const path = require("node:path");
const {
  initializeTestEnvironment, assertFails, assertSucceeds,
} = require("@firebase/rules-unit-testing");
const {
  doc, getDoc, setDoc, updateDoc, deleteDoc, collection, getDocs,
  Timestamp, serverTimestamp, Bytes, addDoc,
} = require("firebase/firestore");

const DAD = "DADTEST0001";
const KID = "KIDTEST0001";
const EVE = "EVETEST0001";
const ROOM = [DAD, KID].sort().join("_");

let env;

test.before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-calldad",
    firestore: {
      rules: fs.readFileSync(path.join(__dirname, "..", "..", "firestore.rules"), "utf8"),
    },
  });
});

test.after(async () => { await env.cleanup(); });
test.beforeEach(async () => { await env.clearFirestore(); });

const db = (uid) => env.authenticatedContext(uid).firestore();
const ringing = (caller, callee, seq) => ({
  status: "RINGING", seq, callerUid: caller, calleeUid: callee,
  offer: { type: "OFFER", sdp: "v=0" }, answer: null,
  callerCandidates: [], calleeCandidates: [], updatedAt: serverTimestamp(),
});
async function seed(data) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "calls", ROOM), data);
  });
}

test("members can pre-read a room that does not exist yet", async () => {
  await assertSucceeds(getDoc(doc(db(DAD), "calls", ROOM)));
});

test("outsiders cannot read the room (no SDP/ICE eavesdropping)", async () => {
  await seed(ringing(KID, DAD, 1));
  await assertFails(getDoc(doc(db(EVE), "calls", ROOM)));
  await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), "calls", ROOM)));
});

test("nobody can list rooms", async () => {
  await assertFails(getDocs(collection(db(DAD), "calls")));
});

test("a member creates the room calling as themselves", async () => {
  await assertSucceeds(setDoc(doc(db(KID), "calls", ROOM), ringing(KID, DAD, 1)));
});

test("caller spoofing and outsider squatting are denied", async () => {
  await assertFails(setDoc(doc(db(KID), "calls", ROOM), ringing(DAD, KID, 1)));
  await assertFails(setDoc(doc(db(EVE), "calls", ROOM), ringing(EVE, KID, 1)));
  await assertFails(setDoc(doc(db(KID), "calls", ROOM), ringing(KID, EVE, 1)));
  await assertFails(setDoc(doc(db(KID), "calls", ROOM), ringing(KID, KID, 1)));
});

test("callee answers within the generation", async () => {
  await seed(ringing(KID, DAD, 1));
  await assertSucceeds(updateDoc(doc(db(DAD), "calls", ROOM), {
    status: "CONNECTED", answer: { type: "ANSWER", sdp: "v=0" }, updatedAt: serverTimestamp(),
  }));
});

test("either member ends; outsiders cannot", async () => {
  await seed({ ...ringing(KID, DAD, 1), status: "CONNECTED" });
  await assertFails(updateDoc(doc(db(EVE), "calls", ROOM), { status: "ENDED" }));
  await assertSucceeds(updateDoc(doc(db(KID), "calls", ROOM), { status: "ENDED" }));
});

test("parties are frozen within a generation", async () => {
  await seed(ringing(KID, DAD, 1));
  await assertFails(updateDoc(doc(db(DAD), "calls", ROOM), { callerUid: DAD, calleeUid: KID }));
});

test("a new generation must be the writer's own RINGING offer", async () => {
  await seed({ ...ringing(KID, DAD, 1), status: "ENDED" });
  await assertSucceeds(setDoc(doc(db(DAD), "calls", ROOM), ringing(DAD, KID, 2)));
  await seed({ ...ringing(KID, DAD, 2), status: "ENDED" });
  await assertFails(setDoc(doc(db(DAD), "calls", ROOM), ringing(KID, DAD, 3)));
  await assertFails(setDoc(doc(db(DAD), "calls", ROOM), { ...ringing(DAD, KID, 3), status: "CONNECTED" }));
});

test("seq can never go backwards", async () => {
  await seed(ringing(KID, DAD, 5));
  await assertFails(updateDoc(doc(db(DAD), "calls", ROOM), { seq: 4 }));
});

test("bogus status is rejected", async () => {
  await seed(ringing(KID, DAD, 1));
  await assertFails(updateDoc(doc(db(DAD), "calls", ROOM), { status: "HACKED" }));
});

test("nobody deletes a room", async () => {
  await seed(ringing(KID, DAD, 1));
  await assertFails(deleteDoc(doc(db(DAD), "calls", ROOM)));
});

const pairing = (uid, peer, minutes = 10) => ({
  uid, peerUid: peer, sessionNonce: "aaaaaaaa:bbbbbbbb",
  expiresAt: Timestamp.fromMillis(Date.now() + minutes * 60_000),
});

test("pairing: owner writes, anyone signed-in gets by id, nobody lists", async () => {
  await assertSucceeds(setDoc(doc(db(KID), "pairings", KID), pairing(KID, DAD)));
  await assertSucceeds(getDoc(doc(db(DAD), "pairings", KID)));
  await assertFails(getDocs(collection(db(DAD), "pairings")));
  await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), "pairings", KID)));
});

test("pairing: no writing someone else's doc, no self-pairing, bounded expiry", async () => {
  await assertFails(setDoc(doc(db(EVE), "pairings", KID), pairing(KID, EVE)));
  await assertFails(setDoc(doc(db(KID), "pairings", KID), pairing(KID, KID)));
  await assertFails(setDoc(doc(db(KID), "pairings", KID), pairing(KID, DAD, 60)));
});

test("users: token doc is owner-only", async () => {
  await assertSucceeds(setDoc(doc(db(KID), "users", KID), { fcmToken: "synthetic" }));
  await assertFails(getDoc(doc(db(EVE), "users", KID)));
  await assertFails(setDoc(doc(db(EVE), "users", KID), { fcmToken: "evil" }));
});

const clip = (from, size = 1000) => ({
  from, audio: Bytes.fromUint8Array(new Uint8Array(size)), durationMs: 1200,
  createdAt: serverTimestamp(),
});

test("ptt: members send, read and delete voice clips; outsiders cannot", async () => {
  const kidClips = collection(db(KID), "calls", ROOM, "ptt");
  const ref = await assertSucceeds(addDoc(kidClips, clip(KID)));
  await assertSucceeds(getDocs(collection(db(DAD), "calls", ROOM, "ptt")));
  await assertFails(getDocs(collection(db(EVE), "calls", ROOM, "ptt")));
  await assertFails(addDoc(collection(db(EVE), "calls", ROOM, "ptt"), clip(EVE)));
  await assertFails(deleteDoc(doc(db(EVE), "calls", ROOM, "ptt", ref.id)));
  await assertSucceeds(deleteDoc(doc(db(DAD), "calls", ROOM, "ptt", ref.id)));
});

test("ptt: no spoofed sender, no oversized clip, no extra fields, no edits", async () => {
  const kidClips = collection(db(KID), "calls", ROOM, "ptt");
  await assertFails(addDoc(kidClips, clip(DAD)));
  await assertFails(addDoc(kidClips, clip(KID, 250000)));
  await assertFails(addDoc(kidClips, { ...clip(KID), note: "x" }));
  const ref = await addDoc(kidClips, clip(KID));
  await assertFails(updateDoc(ref, { durationMs: 1 }));
});
