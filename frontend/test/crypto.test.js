import { test } from 'node:test';
import assert from 'node:assert/strict';
import { webcrypto } from 'node:crypto';
import { generateIdentity, unlockIdentity, encryptMessage, decryptMessage, fingerprint } from '../src/crypto.js';

if (!globalThis.crypto) globalThis.crypto = webcrypto;
const password = 'correct horse battery staple';
const alice = await generateIdentity(password);
const bob = await generateIdentity('another secure password');

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