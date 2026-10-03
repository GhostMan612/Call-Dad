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
const assert = require("node:assert/strict");
const {
  doc, getDoc, setDoc, updateDoc, deleteDoc, collection, getDocs,
  query, where, Timestamp, serverTimestamp, Bytes, addDoc,
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
  await seed(ringing(KID, DAD, 1));
  await assertFails(updateDoc(doc(db(DAD), "calls", ROOM), { seq: 4 }));
});

// ---- same-generation ICE-restart renegotiation (renegotiationRound) ----

// The round is the wire contract that lets a reconnect COMPLETE rather than
// merely be published: it is the only thing that distinguishes a renegotiated
// answer from the one that originally connected the call, because both live at
// the same seq. If the rules rejected the field, every restart would write
// round 0 and the caller would skip the answer forever.
test("renegotiation: a member may swap SDP in place, keeping seq and CONNECTED", async () => {
  await seed({ ...ringing(KID, DAD, 1), status: "CONNECTED" });
  await assertSucceeds(updateDoc(doc(db(KID), "calls", ROOM), {
    offer: { type: "OFFER", sdp: "v=0-restart" },
    renegotiating: true,
    negotiationRound: 1,
    updatedAt: serverTimestamp(),
  }));
});

test("renegotiation: the peer answers in place, same seq, same round", async () => {
  await seed({
    ...ringing(KID, DAD, 1), status: "CONNECTED",
    offer: { type: "OFFER", sdp: "v=0-restart" },
    renegotiating: true, negotiationRound: 1,
  });
  await assertSucceeds(updateDoc(doc(db(DAD), "calls", ROOM), {
    answer: { type: "ANSWER", sdp: "v=0-restart-answer" },
    renegotiating: false,
    negotiationRound: 1,
    updatedAt: serverTimestamp(),
  }));
});

test("renegotiation: a round CANNOT be reused, rolled back, or invented backwards", async () => {
  await seed({ ...ringing(KID, DAD, 1), status: "CONNECTED", negotiationRound: 3 });
  // Going backwards would let a late answer look newer than the offer it answers.
  await assertFails(updateDoc(doc(db(DAD), "calls", ROOM), { negotiationRound: 2 }));
  // So would forging one for a live call with no prior round.
  await seed({ ...ringing(KID, DAD, 1), status: "CONNECTED" });
  await assertFails(updateDoc(doc(db(DAD), "calls", ROOM), { negotiationRound: -1 }));
});

test("renegotiation: it is still a live room, so still undeletable, and outsiders still locked out", async () => {
  await seed({ ...ringing(KID, DAD, 1), status: "CONNECTED", renegotiating: true, negotiationRound: 1 });
  await assertFails(updateDoc(doc(db(EVE), "calls", ROOM), { negotiationRound: 2 }));
  await assertFails(deleteDoc(doc(db(KID), "calls", ROOM)));
  // A renegotiating room must not be usable to spoof a new generation.
  await assertFails(setDoc(doc(db(EVE), "calls", ROOM), ringing(EVE, KID, 2)));
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

// ---------------------------------------------------------------- chat (BP-03)

const msg = (from, body) => ({ from, body, createdAt: serverTimestamp() });
const chatOf = (uid) => collection(db(uid), "calls", ROOM, "chat");

test("chat: the pair can exchange messages; an outsider cannot read or write", async () => {
  const ref = await assertSucceeds(addDoc(chatOf(KID), msg(KID, "hi dad")));
  await assertSucceeds(getDocs(collection(db(DAD), "calls", ROOM, "chat")));
  await assertFails(getDocs(collection(db(EVE), "calls", ROOM, "chat")));
  await assertFails(addDoc(chatOf(EVE), msg(EVE, "hello")));
  await assertFails(deleteDoc(doc(db(EVE), "calls", ROOM, "chat", ref.id)));
  // Either member may delete: either may read everything and forget it.
  await assertSucceeds(deleteDoc(doc(db(DAD), "calls", ROOM, "chat", ref.id)));
});

test("chat: no spoofed sender, no empty or oversized body, no extra fields", async () => {
  await assertFails(addDoc(chatOf(KID), msg(DAD, "impersonating dad")));
  await assertFails(addDoc(chatOf(KID), msg(KID, "")));
  await assertFails(addDoc(chatOf(KID), msg(KID, "x".repeat(501))));
  await assertFails(addDoc(chatOf(KID), { ...msg(KID, "hi"), note: "x" }));
  // A client must not be able to pre-stamp its own receipt.
  await assertFails(addDoc(chatOf(KID), { ...msg(KID, "hi"), deliveredAt: serverTimestamp() }));
  await assertSucceeds(addDoc(chatOf(KID), msg(KID, "x".repeat(500))));
});

test("chat: a member cannot rewrite what a message says (BP-05 no-words-they-did-not-send)", async () => {
  // The one update a mutual allowlist pair must never permit. "Dad" is the only
  // other party, so an editable body means the app can be made to show a child
  // words the grown-up did not send -- or to edit the child's own words into
  // something they never said.
  const ref = await addDoc(chatOf(KID), msg(KID, "original"));
  await assertFails(updateDoc(ref, { body: "rewritten" }));
  await assertFails(updateDoc(ref, { from: DAD }));
  await assertFails(updateDoc(ref, { createdAt: serverTimestamp() }));
});

test("chat: receipts advance for either member, and only receipts", async () => {
  const ref = await addDoc(chatOf(DAD), msg(DAD, "call me when you land"));
  // The receiver stamps delivered; the sender later stamps nothing. Both are
  // members and both directions must work, so a receipt never gets stuck.
  await assertSucceeds(updateDoc(ref, { deliveredAt: serverTimestamp() }));
  await assertSucceeds(updateDoc(ref, { readAt: serverTimestamp() }));
  const ref2 = await addDoc(chatOf(DAD), msg(DAD, "second"));
  await assertSucceeds(updateDoc(ref2, { readAt: serverTimestamp() }));
  // Receipt fields cannot be un-set into a field the rules do not know.
  await assertFails(updateDoc(ref2, { nope: serverTimestamp() }));
});

// ------------------------------------------------------------- photos (BP-04)

const photoMeta = (from, total = 300000, chunks = 2) => ({
  from, totalBytes: total, chunkCount: chunks,
  sha256: "a".repeat(64), mime: "image/webp", width: 1080, height: 810,
  createdAt: serverTimestamp(),
});
const chunkDoc = (from, index, size = 128000) => ({
  from, bytes: Bytes.fromUint8Array(new Uint8Array(size)), index,
  createdAt: serverTimestamp(),
});
const photosOf = (uid) => collection(db(uid), "calls", ROOM, "photos");

test("photos: a member shares, the pair reads, an outsider cannot", async () => {
  const ref = await assertSucceeds(addDoc(photosOf(KID), photoMeta(KID)));
  await assertSucceeds(getDocs(collection(db(DAD), "calls", ROOM, "photos")));
  await assertFails(getDocs(collection(db(EVE), "calls", ROOM, "photos")));
  await assertFails(addDoc(photosOf(EVE), photoMeta(EVE)));
  await assertSucceeds(deleteDoc(doc(db(KID), "calls", ROOM, "photos", ref.id)));
});

test("photos: chunks must be indexed, bounded, and cannot lie about their index", async () => {
  const ref = await addDoc(photosOf(KID), photoMeta(KID));
  const chunks = collection(db(KID), "calls", ROOM, "photos", ref.id, "chunks");
  await assertSucceeds(setDoc(doc(chunks, "0000"), chunkDoc(KID, 0)));
  await assertFails(setDoc(doc(chunks, "0001"), chunkDoc(KID, 5)));      // index != id
  await assertFails(setDoc(doc(chunks, "0002"), chunkDoc(DAD, 2)));      // spoofed sender
  await assertFails(setDoc(doc(chunks, "0003"), chunkDoc(KID, 3, 300000))); // oversized
  await assertSucceeds(getDocs(collection(db(DAD), "calls", ROOM, "photos", ref.id, "chunks")));
  await assertFails(getDocs(collection(db(EVE), "calls", ROOM, "photos", ref.id, "chunks")));
});

test("photos: metadata is immutable, so a digest cannot be swapped after the fact", async () => {
  // Otherwise a failed or truncated transfer can be made to look verified.
  const ref = await addDoc(photosOf(KID), photoMeta(KID));
  await assertFails(updateDoc(ref, { sha256: "b".repeat(64) }));
  await assertFails(updateDoc(ref, { totalBytes: 1 }));
  await assertFails(updateDoc(ref, { from: DAD }));
});

test("photos: oversize totals, bad mime, and a malformed digest are refused", async () => {
  await assertFails(addDoc(photosOf(KID), photoMeta(KID, 900000)));
  await assertFails(addDoc(photosOf(KID), photoMeta(KID, 300000, 64)));
  await assertFails(addDoc(photosOf(KID), { ...photoMeta(KID), mime: "image/png" }));
  await assertFails(addDoc(photosOf(KID), { ...photoMeta(KID), sha256: "short" }));
  await assertFails(addDoc(photosOf(KID), { ...photoMeta(KID), from: DAD }));
});

// ------------------------------------------------ consent + kill switch (BP-05 §2)

const grantDoc = (grantee, grantor, scopes = ["CALL", "TEXT"], seq = 1, days = 30) => ({
  granteeUid: grantee, grantorUid: grantor, scopes, grantSeq: seq,
  grantedAt: serverTimestamp(),
  expiresAt: Timestamp.fromMillis(Date.now() + days * 86_400_000),
});
const revokeDoc = (grantee, grantor, through = 1) => ({
  granteeUid: grantee, grantorUid: grantor, revokesGrantSeq: through,
  revokedAt: serverTimestamp(),
});
const consentsOf = (uid) => collection(db(uid), "calls", ROOM, "consents");
// The argument is the WRITER, not the grantee: revocations are room-level
// auto-id docs, and the grantee travels in the data so the rule can check that
// the grantee is actually a member of the pair.
const revocationsAs = (writer) => collection(db(writer), "calls", ROOM, "revocations");

test("consent: the grown-up may grant the child scopes, and the pair may read them", async () => {
  await assertSucceeds(setDoc(doc(consentsOf(DAD), KID), grantDoc(KID, DAD)));
  await assertSucceeds(getDoc(doc(db(KID), "calls", ROOM, "consents", KID)));
  await assertFails(getDoc(doc(db(EVE), "calls", ROOM, "consents", KID)));
});

test("consent: a CHILD can never authorise itself (the decorative-consent bug)", async () => {
  // The single most likely way a consent feature becomes theatre. The kid's own
  // phone writes a cert granting itself everything, and every client-side check
  // then passes because the cert exists and looks well-formed.
  await assertFails(setDoc(doc(consentsOf(KID), KID), grantDoc(KID, KID)));
  await assertFails(addDoc(revocationsAs(KID), revokeDoc(KID, KID)));});

test("consent: an outsider cannot write, read, or delete a cert", async () => {
  await assertSucceeds(setDoc(doc(consentsOf(DAD), KID), grantDoc(KID, DAD)));
  await assertFails(setDoc(doc(consentsOf(EVE), KID), grantDoc(KID, EVE)));
  await assertFails(getDoc(doc(db(EVE), "calls", ROOM, "consents", KID)));
  await assertFails(deleteDoc(doc(db(DAD), "calls", ROOM, "consents", KID)));
  await assertFails(deleteDoc(doc(db(KID), "calls", ROOM, "consents", KID)));
});

test("consent: a doc id that is not a pair member cannot be written", async () => {
  // Otherwise a member could mint a cert under someone else's id, and a kid
  // could be granted rights by a pair that is not its own.
  await assertFails(setDoc(doc(consentsOf(DAD), EVE), grantDoc(EVE, DAD)));
  // Reading that path now succeeds, and that is the deliberate cost of making
  // read member-level so the app's filtered queries work (see the read rule).
  // It leaks NOTHING: a getDoc on a document that can never be created returns
  // "does not exist" rather than data, because every write path above requires
  // the grantor to be a member and the doc id to be the grantee. The invariant
  // that matters is asserted here -- no cert can ever EXIST under a stranger's
  // id, so there is nothing for the read to reveal.
  const ghost = await assertSucceeds(getDoc(doc(db(DAD), "calls", ROOM, "consents", EVE)));
  assert.strictEqual(ghost.exists(), false, "a non-member cert must not be able to exist");
  // And an outsider cannot read the pair's real certs at all.
  await assertSucceeds(setDoc(doc(consentsOf(DAD), KID), grantDoc(KID, DAD)));
  await assertFails(getDoc(doc(db(EVE), "calls", ROOM, "consents", KID)));
});

test("consent: a grant sequence must move FORWARD, so a stale write cannot roll it back", async () => {
  await assertSucceeds(setDoc(doc(consentsOf(DAD), KID), grantDoc(KID, DAD, ["CALL"], 1)));
  // A renewal goes forward.
  await assertSucceeds(setDoc(doc(consentsOf(DAD), KID), grantDoc(KID, DAD, ["CALL", "TEXT"], 2)));
  // Re-writing the same seq is refused: this is what makes "re-grant after a
  // revoke" a deliberate act (a HIGHER seq) rather than something a retried or
  // out-of-order write achieves by accident.
  await assertFails(setDoc(doc(consentsOf(DAD), KID), grantDoc(KID, DAD, ["CALL"], 2)));
  await assertFails(setDoc(doc(consentsOf(DAD), KID), grantDoc(KID, DAD, ["CALL"], 1)));
});

test("consent: the kill switch is APPEND-ONLY and cannot be edited or removed", async () => {
  await assertSucceeds(setDoc(doc(consentsOf(DAD), KID), grantDoc(KID, DAD)));
  const ref = await assertSucceeds(addDoc(revocationsAs(DAD), revokeDoc(KID, DAD, 1)));
  // Neither the grown-up nor the kid can weaken a written revocation. This is
  // the whole point of putting revocations where a grant write cannot reach:
  // a `revokedAt` flag on the grant doc is destroyed by the next grant.
  await assertFails(updateDoc(ref, { revokesGrantSeq: 0 }));
  await assertFails(deleteDoc(ref));
  await assertFails(deleteDoc(ref));
  // And a grant cannot be deleted to make room: losing the document must never
  // hand a device more authority, and never silently un-revoke.
  await assertFails(deleteDoc(doc(db(DAD), "calls", ROOM, "consents", KID)));
  // The pair can still READ the revocation, which is what the gate needs.
  await assertSucceeds(getDocs(revocationsAs(KID)));
});

test("consent: a revocation aimed at a non-member, or written by a stranger, is refused", async () => {
  // granteeUid travels in the data, so this is the check that stops a member
  // revoking -- or "granting" -- a device that is not in their pair.
  await assertFails(addDoc(revocationsAs(DAD), revokeDoc(EVE, DAD, 1)));
  await assertFails(addDoc(revocationsAs(EVE), revokeDoc(EVE, DAD, 1)));
  // The writer must be the grantee's PEER, so a member cannot revoke the other
  // member's own consent either.
  await assertFails(addDoc(revocationsAs(DAD), revokeDoc(DAD, DAD, 1)));
});

test("consent: a client cannot back-date a revocation", async () => {
  // Otherwise a revocation could be written with a stale timestamp and the gate,
  // which orders on seq and checks the server timestamp, would be fed a forgery.
  await assertFails(setDoc(doc(revocationsAs(DAD), "forged"), {
    granteeUid: KID, grantorUid: DAD, revokesGrantSeq: 1,
    revokedAt: Timestamp.fromMillis(0),
  }));
});

test("consent: unknown scopes, empty scopes, a bad seq, and a mismatched grantee are refused", async () => {
  await assertFails(setDoc(doc(consentsOf(DAD), KID), grantDoc(KID, DAD, [])));
  await assertFails(setDoc(doc(consentsOf(DAD), KID), grantDoc(KID, DAD, ["CALL", "ADMIN"])));
  // VOICE is a REAL scope -- the Ask Helper microphone had none, so the kill
  // switch left a live mic in a child's hand while promising nothing was
  // allowed. It must round-trip through the rules, or it is decorative.
  await assertSucceeds(setDoc(doc(consentsOf(DAD), KID), grantDoc(KID, DAD, ["CALL", "VOICE"], 2)));
  // Seq 0 is below the floor, so "nothing has been granted" cannot be claimed.
  await assertFails(setDoc(doc(consentsOf(DAD), KID), grantDoc(KID, DAD, ["CALL"], 0)));
  // granteeUid must match the document id, or one cert could shadow another.
  await assertFails(setDoc(doc(consentsOf(DAD), KID), grantDoc(DAD, DAD, ["CALL"])));
  // A decade-long grant, and a grant that is already over, are both refused.
  await assertFails(setDoc(doc(consentsOf(DAD), KID), grantDoc(KID, DAD, ["CALL"], 1, 3650)));
  await assertFails(setDoc(doc(consentsOf(DAD), KID),
    { ...grantDoc(KID, DAD, ["CALL"], 1), expiresAt: Timestamp.fromMillis(Date.now() - 1000) }));
});

// THIS TEST'S SHAPE CHANGED DELIBERATELY, and the old shape was the bug.
//
// It used to assert `assertFails(getDocs(consentsOf(DAD)))` -- "nobody can list
// every cert in a room" -- and it PASSED. But it passed for the wrong reason: a
// path-wildcard rule cannot be proven safe for a query, so it denied EVERY
// listing, including the two filtered queries the app runs on every launch. A
// green assertion was pinning the app's own consent listeners shut.
//
// The boundary that actually matters is WHO, not WHICH QUERY: the two members of
// the pair may read the pair's consent state (they are the only parties, and the
// room id is the two sorted UIDs), and nobody else may read anything.
test("consent: the PAIR may read the room's certs; nobody else can", async () => {
  await assertSucceeds(setDoc(doc(consentsOf(DAD), KID), grantDoc(KID, DAD)));
  // A member may now list their own room's certs -- this is what the app needs.
  await assertSucceeds(getDocs(consentsOf(DAD)));
  // An authenticated stranger still cannot, and neither can an anonymous client.
  await assertFails(getDocs(consentsOf(EVE)));
  await assertFails(getDocs(collection(env.unauthenticatedContext().firestore(),
    "calls", ROOM, "consents")));
  // Revocations remain readable only by the two named parties.
  await assertFails(getDocs(revocationsAs(EVE)));
  // And no member can list ANOTHER pair's room, because the room id is the
  // authorization: an outsider's room is a different document path entirely.
  await assertFails(getDocs(collection(db(EVE), "calls", "EVE_OTHER", "consents")));
});

// ---------------------------------------------------------------------------
// AUDIT 2026-10-02 — four gaps in cells the suite never asserted. Every test
// below failed against the rules as they stood. A gate nobody watched fail is a
// gate nobody can trust, so these are here to be seen going red first.
// ---------------------------------------------------------------------------

// ###########################################################################
// # KNOWN GAP, NOT A PASS. THIS TEST ASSERTS THE INSECURE TRUTH ON PURPOSE. #
// ###########################################################################
//
// FINDING 1 — OPEN. The rules make a SELF-grant impossible and the suite
// asserted exactly that. The CROSS grant is still permitted, and it cannot be
// closed in the rules at all.
//
// WHY NO RULE CAN CLOSE IT: the room id is the two sorted UIDs and nothing in
// Firestore says which of them is the grown-up. `grantorIsThePeer` requires
// grantor != grantee, which BOTH directions satisfy, so `consents/DAD` written
// by KID is byte-for-byte indistinguishable from `consents/KID` written by DAD.
// Any rule that denied the first would deny the legitimate second.
//
// WHY IT MATTERS: the client's role discriminator is "did I author any cert?"
// (ConsentStore: authoredReg where grantorUid == ownUid). So a child writing
// `consents/DAD` makes `isGrantor` true on the CHILD'S OWN PHONE -- and
// `recompute()` short-circuits on isGrantor, so a subsequent parental revocation
// is never consulted. The kill switch would become a no-op on the one device it
// exists to protect. Grants are delete:false, so no parent action recovers it.
//
// This is the decorative-consent bug again, aimed the other way. Closing it
// needs a TRUST ROOT -- an asymmetric role decided at pairing time (the QR
// payload already carries {v, uid, nonce}) or a signed credential -- which is an
// ADR-017 architecture decision for the operator, not a rules edit. Do NOT
// "fix" this by adding a role field that any member can also write: that is the
// same bug wearing a hat.
//
// WHEN IT IS FIXED, this test fails, and that failure is the point: flip the two
// assertSucceeds below to assertFails, delete this banner, and record the
// trust-root decision in ADR-017. Until then the gap is documented, not hidden.
test("consent: KNOWN GAP - a cross-grant IS permitted by the rules (needs a trust root)", async () => {
  // The child writing a cert whose GRANTEE is the parent: currently ALLOWED.
  await assertSucceeds(setDoc(doc(consentsOf(KID), DAD), grantDoc(DAD, KID)));
  // It can also renew its own forgery, because grantFieldsOk only checks that
  // grantorUid == request.auth.uid.
  await assertSucceeds(updateDoc(doc(db(KID), "calls", ROOM, "consents", DAD),
    grantDoc(DAD, KID, ["CALL"], 2)));
  // The legitimate direction is unchanged and must keep working.
  await assertSucceeds(setDoc(doc(consentsOf(DAD), KID), grantDoc(KID, DAD)));
  // What IS closed, and stays closed: a member cannot rewrite a peer's cert to
  // change who granted it, because update re-checks grantorIsThePeer against
  // the OLD data too. That is what stops a silent role transfer.
  await assertFails(updateDoc(doc(db(KID), "calls", ROOM, "consents", DAD),
    grantDoc(DAD, DAD, ["CALL"], 3)));
});

// FINDING 2. `allow read: if isMember() && granteeUid in members()` reads as
// "only the two named parties", and the getDoc assertion passed. But Firestore
// must prove a query is safe for EVERY doc the query could return, and
// `granteeUid` here is a PATH wildcard, not a field. A whereEqualTo on a FIELD
// does not narrow a path wildcard, so the engine still considers
// `consents/EVETEST0001` -- where "EVE" in members() is false -- and denies.
//
// The app never getDocs. It runs two FILTERED queries
// (ConsentStore.kt: whereEqualTo granteeUid/grantorUid == ownUid). Both fail
// closed to an empty list, so _scopes = empty and _decision = Denied -- on BOTH
// phones. That is the whole app inert after a real grant, with no error
// anywhere, and it looks exactly like "correctly denied". The revokeReg query
// works because the revocations read rule is a bare isMember() with no
// wildcard, which is the asymmetry that proves the mechanism.
test("consent: the app's OWN filtered consent queries are permitted", async () => {
  await assertSucceeds(setDoc(doc(consentsOf(DAD), KID), grantDoc(KID, DAD)));
  // The child's grant listener. This is the exact query the app runs.
  await assertSucceeds(getDocs(
    query(consentsOf(KID), where("granteeUid", "==", KID))));
  // The role listener that decides isGrantor. If THIS fails, the parent's phone
  // can never grant at all.
  await assertSucceeds(getDocs(
    query(consentsOf(DAD), where("grantorUid", "==", DAD))));
  // And a non-member's own filter still cannot widen it: EVE is not in the room
  // so even querying for EVE is denied by isMember() upstream.
  await assertFails(getDocs(
    query(consentsOf(EVE), where("granteeUid", "==", EVE))));
});

// FINDING 3. chunkOk() bounded each chunk to 256KB but never bounded how many
// chunks there are, and never required the manifest to exist. 10,000 in-bounds
// chunks is ~2.5GB under a photo id whose manifest declares 800KB -- 3,900x.
// PhotoClient fetches the chunks collection UNBOUNDED and materialises every one
// into a HashMap before any validation, so the aggregate cap exists only in the
// client, downstream of the download and the allocation. PhotoTransfer's
// MAX_CHUNKS is a courtesy, not a control. (Not a data-integrity forgery: the
// digest check still rejects it, so nothing can be made to look verified.)
// The manifest CANNOT be a precondition: the client writes chunks first and the
// manifest last (PhotoClient: chunks-then-manifest, because the other order lets
// a receiver see a manifest and fetch chunks that do not exist yet). So the
// ceiling is bound to the INDEX instead, which is what actually bounds the
// number of chunks a writer may create -- each chunk was individually legal, so
// without it 10,000 of them is ~2.5GB under a photo id whose manifest declares
// at most 800KB.
test("photos: a chunk index is bounded, so the chunk COUNT is bounded", async () => {
  const chunksOf = (uid, photoId) =>
    collection(db(uid), "calls", ROOM, "photos", photoId, "chunks");
  const chunk = (from, index) => ({
    from, bytes: Bytes.fromUint8Array(new Uint8Array(128000)),
    index, createdAt: serverTimestamp(),
  });
  const manifest = (from, count) => ({
    from, totalBytes: 800000, chunkCount: count,
    sha256: "a".repeat(64), mime: "image/webp",
    width: 1080, height: 810, createdAt: serverTimestamp(),
  });

  // Chunks with no manifest yet: the real send order, so this must WORK.
  await assertSucceeds(setDoc(doc(chunksOf(KID, "P1"), "0000"), chunk(KID, 0)));
  await assertSucceeds(setDoc(doc(chunksOf(KID, "P1"), "0001"), chunk(KID, 1)));
  // The manifest then lands, as the real client writes it.
  await assertSucceeds(setDoc(doc(db(KID), "calls", ROOM, "photos", "P1"), manifest(KID, 2)));
  // Past the ceiling: refused even though every individual field is in bounds.
  await assertFails(setDoc(doc(chunksOf(KID, "P1"), "0032"), chunk(KID, 32)));
  await assertFails(setDoc(doc(chunksOf(KID, "P1"), "9999"), chunk(KID, 9999)));
  // Out-of-order zero-padding must not become an alias for a different index.
  await assertFails(setDoc(doc(chunksOf(KID, "P1"), "0002"), chunk(KID, 7)));
  // A negative index is not a name for the front of the sequence.
  await assertFails(setDoc(doc(chunksOf(KID, "P1"), "00ff"), chunk(KID, -1)));
});

// FINDING 4. The monotonic negotiationRound guard is applied to EVERY update,
// including one that advances seq. publishOffer uses a non-merging set(), which
// does not delete unmentioned fields -- so negotiationRound SURVIVES into the
// next call. Meanwhile beginGeneration resets nextNegotiationRound to 1 on every
// new attempt. So call #2's first ICE restart publishes round 1 against a room
// still carrying round 2 -> PERMISSION_DENIED, forever, because the client only
// advances its counter on a CONFIRMED publish and so retries round 1 forever.
// The user sees a silently dropped call, not an error, and no client anywhere
// deletes the room doc so the stale round never clears.
//
// The rule that must hold: monotonic WITHIN a generation, RESET when seq moves.
test("renegotiation: the round RESETS when a new generation (higher seq) is published", async () => {
  // Call #1 ran two restarts, so the room doc carries round 2 at seq 1.
  await seed({ ...ringing(KID, DAD, 1), status: "CONNECTED", negotiationRound: 2 });
  // Call #2: a fresh offer at seq 2. The round must be allowed to start again
  // from 0/1, or ICE restart is dead for the rest of this pairing's life.
  await assertSucceeds(setDoc(doc(db(KID), "calls", ROOM), {
    ...ringing(KID, DAD, 2), negotiationRound: 0,
  }));
  // And the new generation's first restart is writable.
  await assertSucceeds(updateDoc(doc(db(KID), "calls", ROOM), {
    offer: { type: "OFFER", sdp: "v=0-call2-restart" },
    renegotiating: true, negotiationRound: 1, updatedAt: serverTimestamp(),
  }));
  // Monotonicity still holds inside generation 2: no going backwards.
  await assertFails(updateDoc(doc(db(KID), "calls", ROOM), { negotiationRound: 0 }));
  // Generation 3 is the same: reset to 0, then one step forward.
  await assertSucceeds(setDoc(doc(db(KID), "calls", ROOM), {
    ...ringing(KID, DAD, 3), negotiationRound: 0,
  }));
  await assertSucceeds(updateDoc(doc(db(KID), "calls", ROOM), {
    renegotiating: true, negotiationRound: 1, updatedAt: serverTimestamp(),
  }));
  // A peer cannot jump the ladder within one generation -- only the caller may,
  // and only by the step it is entitled to. Asserting that a *stale* answer from
  // the previous call cannot pose as current: the round went to 2, so a late
  // answer written for round 1 is refused.
  await assertSucceeds(updateDoc(doc(db(DAD), "calls", ROOM), {
    renegotiating: true, negotiationRound: 2, updatedAt: serverTimestamp(),
  }));
  await assertFails(updateDoc(doc(db(DAD), "calls", ROOM), {
    answer: { type: "ANSWER", sdp: "v=0-call3-late-answer-for-round-1" },
    renegotiating: false, negotiationRound: 1, updatedAt: serverTimestamp(),
  }));
  // And an outsider still cannot restart anything.
  await assertFails(updateDoc(doc(db(EVE), "calls", ROOM), {
    renegotiating: true, negotiationRound: 5, updatedAt: serverTimestamp(),
  }));
});

// The gaps the audit listed as SAFE-but-untested. Asserted so a future edit
// cannot quietly make them unsafe.
test("consent: a member cannot overwrite a peer's existing grant to seize the role", async () => {
  await assertSucceeds(setDoc(doc(consentsOf(DAD), KID), grantDoc(KID, DAD, ["CALL"], 1)));
  // The kid tries to take over the parent's own cert. grantorIsThePeer is
  // checked against BOTH the new and the old data, so the parent stays the
  // grantor of the parent's own grant.
  await assertFails(updateDoc(doc(db(KID), "calls", ROOM, "consents", DAD),
    grantDoc(DAD, KID, ["CALL"], 2)));
  // And nobody can list the users collection, where fcmTokens live.
  await assertFails(getDocs(collection(db(EVE), "users")));
});
