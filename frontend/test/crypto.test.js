import { test } from 'node:test';
import assert from 'node:assert/strict';
import { webcrypto } from 'node:crypto';
import { generateIdentity, unlockIdentity, encryptMessage, decryptMessage, fingerprint, reencryptIdentity } from '../src/crypto.js';
import { encodeContent, decodeContent } from '../src/messageContent.js';

if (!globalThis.crypto) globalThis.crypto = webcrypto;
const password = 'correct horse battery staple';
const alice = await generateIdentity(password);
const bob = await generateIdentity('another secure password');

test('password changes rewrap the same identity and preserve existing history', async () => {
  const message = { ...await encryptMessage('old history', bob.publicKey, alice.publicKey), senderId: 'bob' };
  const changed = await reencryptIdentity(alice.backup, password, 'replacement secure password', alice.publicKey);
  assert.equal(changed.publicKey, alice.publicKey);
  assert.notEqual(changed.salt, alice.backup.salt);
  const key = await unlockIdentity(changed, 'replacement secure password', alice.publicKey);
  assert.equal(key.extractable, false);
  assert.equal(await decryptMessage(message, key, 'alice'), 'old history');
  await assert.rejects(unlockIdentity(changed, password, alice.publicKey));
  await assert.rejects(reencryptIdentity(alice.backup, 'wrong password', 'replacement secure password', alice.publicKey));
  await assert.rejects(reencryptIdentity(alice.backup, password, 'replacement secure password', bob.publicKey));
  // Failed/cancelled changes do not mutate the old backup.
  await unlockIdentity(alice.backup, password, alice.publicKey);
});

test('encrypted private-key backup unlocks and remains non-extractable', async () => {
  const restored = await unlockIdentity(JSON.parse(JSON.stringify(alice.backup)), password, alice.publicKey);
  assert.equal(restored.extractable, false);
  await assert.rejects(crypto.subtle.exportKey('pkcs8', restored));
  assert.equal(alice.backup.encrypted.includes('PRIVATE KEY'), false);
});
test('incorrect password and mismatched identity are rejected', async () => {
  await assert.rejects(unlockIdentity(alice.backup, 'incorrect password', alice.publicKey));
  await assert.rejects(unlockIdentity(alice.backup, password, bob.publicKey));
});
test('sender and recipient can decrypt Unicode and multiline messages', async () => {
  const text = 'Hello 🔐\nPrivate message — 你好';
  const message = { ...await encryptMessage(text, alice.publicKey, bob.publicKey), senderId: 'alice' };
  assert.equal(await decryptMessage(message, alice.privateKey, 'alice'), text);
  assert.equal(await decryptMessage(message, bob.privateKey, 'bob'), text);
  assert.notEqual(message.senderKey, message.recipientKey);
  assert.equal(JSON.stringify(message).includes(text), false);
});
test('ciphertext and associated identifier tampering are rejected', async () => {
  const m = { ...await encryptMessage('secret', alice.publicKey, bob.publicKey), senderId: 'alice' };
  await assert.rejects(decryptMessage({ ...m, clientId: crypto.randomUUID() }, bob.privateKey, 'bob'));
  const ciphertext = m.ciphertext[0] === 'A' ? `B${m.ciphertext.slice(1)}` : `A${m.ciphertext.slice(1)}`;
  await assert.rejects(decryptMessage({ ...m, ciphertext }, bob.privateKey, 'bob'));
});
test('wrong private key cannot decrypt recipient envelope', async () => {
  const m = { ...await encryptMessage('secret', alice.publicKey, bob.publicKey), senderId: 'alice' };
  await assert.rejects(decryptMessage(m, alice.privateKey, 'not-alice'));
});
test('each encryption has a new IV and ciphertext; fingerprints are stable', async () => {
  const a = await encryptMessage('same', alice.publicKey, bob.publicKey);
  const b = await encryptMessage('same', alice.publicKey, bob.publicKey);
  assert.notEqual(a.iv, b.iv);
  assert.notEqual(a.ciphertext, b.ciphertext);
  assert.equal(await fingerprint(alice.publicKey), await fingerprint(alice.publicKey));
  assert.notEqual(await fingerprint(alice.publicKey), await fingerprint(bob.publicKey));
});
test('self-encrypted drafts and replies contain no persisted plaintext', async () => {
  const content = encodeContent('private draft text', { id: 'reply-id', text: 'private reply quote' });
  const draft = { ...await encryptMessage(content, alice.publicKey, alice.publicKey), senderId: 'alice' };
  assert.equal(JSON.stringify(draft).includes('private draft text'), false);
  assert.equal(JSON.stringify(draft).includes('private reply quote'), false);
  assert.deepEqual(decodeContent(await decryptMessage(draft, alice.privateKey, 'alice')), {
    text: 'private draft text', reply: { id: 'reply-id', text: 'private reply quote' },
  });
  await assert.rejects(decryptMessage(draft, bob.privateKey, 'alice'));
});