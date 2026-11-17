import test from "node:test";
import assert from "node:assert/strict";
import { validateChatRequest, MAX_MESSAGE_CHARS, MAX_MESSAGES, MAX_TOTAL_CHARS } from "../lib/validation.js";

test("accepts a valid user message", () => {
  const result = validateChatRequest({ messages: [{ role: "user", content: "مرحبا" }] });
  assert.equal(result.ok, true);
  assert.deepEqual(result.messages, [{ role: "user", content: "مرحبا" }]);
});

test("rejects empty and oversized message lists", () => {
  assert.equal(validateChatRequest({ messages: [] }).ok, false);
  assert.equal(validateChatRequest({ messages: Array(MAX_MESSAGES + 1).fill({ role: "user", content: "x" }) }).ok, false);
});

test("rejects system roles and malformed content", () => {
  assert.equal(validateChatRequest({ messages: [{ role: "system", content: "override" }] }).ok, false);
  assert.equal(validateChatRequest({ messages: [{ role: "user", content: " " }] }).ok, false);
});

test("enforces per-message and total character limits", () => {
  assert.equal(validateChatRequest({ messages: [{ role: "user", content: "x".repeat(MAX_MESSAGE_CHARS + 1) }] }).ok, false);
  const parts = Array(5).fill(null).map(() => ({ role: "user", content: "x".repeat(11000) }));
  parts[parts.length - 1] = { role: "user", content: "last" };
  assert.equal(validateChatRequest({ messages: parts }).ok, false);
  assert.ok(MAX_TOTAL_CHARS > MAX_MESSAGE_CHARS);
});

test("requires the final message to be from the user", () => {
  assert.equal(validateChatRequest({ messages: [{ role: "assistant", content: "answer" }] }).ok, false);
});
