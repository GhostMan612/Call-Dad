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
  await assertFails(getDoc(doc(db(DAD), "calls", ROOM, "consents", EVE)));
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
  // Seq 0 is below the floor, so "nothing has been granted" cannot be claimed.
  await assertFails(setDoc(doc(consentsOf(DAD), KID), grantDoc(KID, DAD, ["CALL"], 0)));
  // granteeUid must match the document id, or one cert could shadow another.
  await assertFails(setDoc(doc(consentsOf(DAD), KID), grantDoc(DAD, DAD, ["CALL"])));
  // A decade-long grant, and a grant that is already over, are both refused.
  await assertFails(setDoc(doc(consentsOf(DAD), KID), grantDoc(KID, DAD, ["CALL"], 1, 3650)));
  await assertFails(setDoc(doc(consentsOf(DAD), KID),
    { ...grantDoc(KID, DAD, ["CALL"], 1), expiresAt: Timestamp.fromMillis(Date.now() - 1000) }));
});

test("consent: nobody can list every cert in a room", async () => {
  await assertSucceeds(setDoc(doc(consentsOf(DAD), KID), grantDoc(KID, DAD)));
  await assertFails(getDocs(consentsOf(DAD)));
  // Revocations are readable only by the two named parties.
  await assertFails(getDocs(revocationsAs(EVE)));
});
