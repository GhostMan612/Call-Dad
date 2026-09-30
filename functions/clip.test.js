// Host test for the PTT clip notification decision.
// Run: node --test functions/clip.test.js
const test = require("node:test");
const assert = require("node:assert");

const { clipTarget } = require("./clip");

const ROOM = "aaaaaaaaaa_bbbbbbbbbb";
const clip = (from, extra = {}) => ({ from, durationMs: 1200, ...extra });

test("a new clip notifies the other member", () => {
  assert.deepStrictEqual(clipTarget(ROOM, null, clip("aaaaaaaaaa")),
    { peerUid: "bbbbbbbbbb", durationMs: 1200 });
});

test("the other direction addresses the other member too", () => {
  assert.deepStrictEqual(clipTarget(ROOM, null, clip("bbbbbbbbbb")),
    { peerUid: "aaaaaaaaaa", durationMs: 1200 });
});

test("an update does not re-notify (clips are immutable)", () => {
  assert.strictEqual(clipTarget(ROOM, clip("aaaaaaaaaa"), clip("aaaaaaaaaa")), null);
});

test("a delete does not notify about a message that is gone", () => {
  assert.strictEqual(clipTarget(ROOM, clip("aaaaaaaaaa"), null), null);
});

test("a clip from outside the room notifies nobody", () => {
  assert.strictEqual(clipTarget(ROOM, null, clip("cccccccccc")), null);
});

test("a malformed room id notifies nobody", () => {
  assert.strictEqual(clipTarget("not_a_pair_at_all", null, clip("aaaaaaaaaa")), null);
  assert.strictEqual(clipTarget("solo", null, clip("solo")), null);
});

test("a clip with no usable duration notifies nobody", () => {
  // A notification that leads to an unplayable message is worse than silence.
  assert.strictEqual(clipTarget(ROOM, null, clip("aaaaaaaaaa", { durationMs: 0 })), null);
  assert.strictEqual(clipTarget(ROOM, null, clip("aaaaaaaaaa", { durationMs: -5 })), null);
  assert.strictEqual(clipTarget(ROOM, null, { from: "aaaaaaaaaa" }), null);
});
