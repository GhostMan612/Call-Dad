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

test("a LIVE room is never deletable, by anyone", async () => {
  // Cleanup (below) is safe only because a ringing/connected room is
  // undeletable. If this ever passes, a ringing call can be cancelled by
  // deleting the doc, which is the same class of bug as the stale teardown.
  await seed(ringing(KID, DAD, 1));
  await assertFails(deleteDoc(doc(db(DAD), "calls", ROOM)));
  await assertFails(deleteDoc(doc(db(KID), "calls", ROOM)));
  await assertFails(deleteDoc(doc(db(EVE), "calls", ROOM)));
});

test("a TERMINAL room can be cleaned up by a member, so a reinstall leaves no orphan", async () => {
  // Was `delete: if false` for every case, which meant that after a reinstall
  // (new UID, new room) the old room and its clips were written by an account
  // that no longer existed on the phone and were undeletable by anyone --
  // accruing storage forever with no way to reclaim it.
  await seed({ ...ringing(KID, DAD, 1), status: "ENDED" });
  await assertSucceeds(deleteDoc(doc(db(DAD), "calls", ROOM)));
  await seed({ ...ringing(KID, DAD, 1), status: "DECLINED" });
  await assertSucceeds(deleteDoc(doc(db(KID), "calls", ROOM)));
});

test("a terminal room is still not deletable by an outsider", async () => {
  await seed({ ...ringing(KID, DAD, 1), status: "ENDED" });
  await assertFails(deleteDoc(doc(db(EVE), "calls", ROOM)));
});

const pairing = (uid, peer, minutes = 10) => ({
  uid, peerUid: peer, sessionNonce: "aaaaaaaa:bbbbbbbb",
  expiresAt: Timestamp.fromMillis(Date.now() + minutes * 60_000),
});

test("pairing: owner writes, the NAMED peer reads, nobody else does", async () => {
  await assertSucceeds(setDoc(doc(db(KID), "pairings", KID), pairing(KID, DAD)));
  // The handshake needs exactly this one read: the peer confirms the doc
  // names them. That is the ONLY other reader.
  await assertSucceeds(getDoc(doc(db(DAD), "pairings", KID)));
  await assertFails(getDocs(collection(db(DAD), "pairings")));
  await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), "pairings", KID)));
});

test("pairing: a signed-in stranger can NOT read a pairing doc (K9-class leak)", async () => {
  // Was `allow get: if request.auth != null`, so ANY anonymous install could
  // read any pairing doc by id and learn a real UID, a real peer UID, and a
  // live handshake nonce -- which is half of hijacking a pairing. `list` was
  // already denied, but ids leak through other channels.
  await assertSucceeds(setDoc(doc(db(KID), "pairings", KID), pairing(KID, DAD)));
  await assertFails(getDoc(doc(db(EVE), "pairings", KID)));
});

test("pairing: a live handshake cannot be deleted early", async () => {
  // A phone mid-scan must not have its handshake yanked away by the other
  // side, which would strand the pair between the two writes.
  await assertSucceeds(setDoc(doc(db(KID), "pairings", KID), pairing(KID, DAD)));
  await assertFails(deleteDoc(doc(db(KID), "pairings", KID)));
});

test("pairing: an EXPIRED handshake is clearable, or it leaks forever", async () => {
  // Written with rules disabled, because `create` legitimately refuses an
  // already-expired doc (a phone that died mid-handshake is exactly the case
  // that leaves one behind, and it must not be clearable-by-nobody).
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "pairings", KID), pairing(KID, DAD, -5));
  });
  await assertSucceeds(deleteDoc(doc(db(KID), "pairings", KID)));
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
