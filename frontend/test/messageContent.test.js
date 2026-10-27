import { test } from 'node:test';
import assert from 'node:assert/strict';
import { encodeContent, decodeContent, sortConversations } from '../src/messageContent.js';

test('legacy messages remain plain text', () => {
  assert.deepEqual(decodeContent('hello {"text":"world"}'), { text: 'hello {"text":"world"}', reply: null });
});
test('versioned content round trips text and bounded reply previews', () => {
  const content = decodeContent(encodeContent('hello 🔐', { id: 'message-id', text: 'a'.repeat(500) }));
  assert.equal(content.text, 'hello 🔐');
  assert.equal(content.reply.id, 'message-id');
  assert.equal(content.reply.text.length, 240);
  assert.deepEqual(decodeContent(encodeContent('no reply')), { text: 'no reply', reply: null });
});
test('malformed tagged messages fail closed', () => {
  assert.throws(() => decodeContent('cipherchat:content:v1:{"text":42,"reply":null}'));
  assert.throws(() => decodeContent('cipherchat:content:v1:{"text":"ok","reply":{"id":12}}'));
});
test('pinned conversations precede recent activity without mutating input', () => {
  const items = [
    { id: 'old', pinned: false, lastActivityAt: '2026-01-01' },
    { id: 'new', pinned: false, lastActivityAt: '2026-02-01' },
    { id: 'pin', pinned: true, lastActivityAt: '2025-01-01' },
  ];
  assert.deepEqual(sortConversations(items).map(c => c.id), ['pin', 'new', 'old']);
  assert.equal(items[0].id, 'old');
});