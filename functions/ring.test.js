// Host test for the ring decision. Run: node --test functions/ring.test.js
const test = require("node:test");
const assert = require("node:assert");

const { ringTarget } = require("./ring");

const ROOM = "aaaaaaaaaa_bbbbbbbbbb";
const ringing = (seq, extra = {}) => ({
  status: "RINGING", seq, callerUid: "aaaaaaaaaa", calleeUid: "bbbbbbbbbb", ...extra,
});

test("first-ever ring (create) pushes", () => {
  assert.deepStrictEqual(ringTarget(ROOM, null, ringing(1)), { calleeUid: "bbbbbbbbbb", seq: 1 });
});

test("seq bump while already ringing pushes (try again)", () => {
  assert.ok(ringTarget(ROOM, ringing(1), ringing(2)));
});

test("ICE trickle within a ringing generation does not re-push", () => {
  assert.strictEqual(ringTarget(ROOM, ringing(3), ringing(3, { callerCandidates: [1] })), null);
});

test("answer / end never push", () => {
  assert.strictEqual(ringTarget(ROOM, ringing(3), { ...ringing(3), status: "CONNECTED" }), null);
  assert.strictEqual(ringTarget(ROOM, ringing(3), { ...ringing(3), status: "ENDED" }), null);
});

test("ENDED -> RINGING at a new seq pushes", () => {
  assert.ok(ringTarget(ROOM, { ...ringing(4), status: "ENDED" }, ringing(5)));
});

test("parties outside the room id never push", () => {
  assert.strictEqual(ringTarget(ROOM, null, ringing(1, { calleeUid: "cccccccccc" })), null);
  assert.strictEqual(ringTarget("family_channel_x", null, ringing(1)), null);
  assert.strictEqual(ringTarget(ROOM, null, ringing(1, { calleeUid: "aaaaaaaaaa" })), null);
});
