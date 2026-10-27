const encoder = new TextEncoder();
const decoder = new TextDecoder();
const RSA = { name: 'RSA-OAEP', hash: 'SHA-256' };
const ITERATIONS = 600000;

export function base64(bytes) {
  return btoa(Array.from(new Uint8Array(bytes), byte => String.fromCharCode(byte)).join(''));
}
export function unbase64(value) {
  return Uint8Array.from(atob(value), char => char.charCodeAt(0));
}
async function passwordKey(password, salt, iterations, usage) {
  const material = await crypto.subtle.importKey('raw', encoder.encode(password), 'PBKDF2', false, ['deriveKey']);
  return crypto.subtle.deriveKey({ name: 'PBKDF2', hash: 'SHA-256', salt, iterations }, material,
    { name: 'AES-GCM', length: 256 }, false, usage);
}
export async function generateIdentity(password) {
  const pair = await crypto.subtle.generateKey({ ...RSA, modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]) },
    true, ['encrypt', 'decrypt']);
  const publicKey = base64(await crypto.subtle.exportKey('spki', pair.publicKey));
  const raw = new Uint8Array(await crypto.subtle.exportKey('pkcs8', pair.privateKey));
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const iv = crypto.getRandomValues(new Uint8Array(12));
  try {
    const wrappingKey = await passwordKey(password, salt, ITERATIONS, ['encrypt']);
    const encrypted = await crypto.subtle.encrypt({ name: 'AES-GCM', iv, additionalData: encoder.encode(publicKey) }, wrappingKey, raw);
    const backup = { version: 1, publicKey, salt: base64(salt), iv: base64(iv), iterations: ITERATIONS, encrypted: base64(encrypted) };
    // Re-import non-extractable so the live session cannot accidentally export a plaintext key.
    const privateKey = await crypto.subtle.importKey('pkcs8', raw, RSA, false, ['decrypt']);
    return { publicKey, privateKey, backup };
  } finally { raw.fill(0); }
}
export async function unlockIdentity(backup, password, expectedPublicKey) {
  if (backup?.version !== 1 || backup.publicKey !== expectedPublicKey ||
      !Number.isInteger(backup.iterations) || backup.iterations < ITERATIONS || backup.iterations > 2000000 ||
      typeof backup.encrypted !== 'string' || backup.encrypted.length > 20000)
    throw new Error('This backup does not match the account or has an unsupported format.');
  const salt = unbase64(backup.salt), iv = unbase64(backup.iv);
  if (salt.length !== 16 || iv.length !== 12) throw new Error('Invalid key backup.');
  const key = await passwordKey(password, salt, backup.iterations, ['decrypt']);
  let raw;
  try {
    raw = new Uint8Array(await crypto.subtle.decrypt({ name: 'AES-GCM', iv,
      additionalData: encoder.encode(expectedPublicKey) }, key, unbase64(backup.encrypted)));
    const privateKey = await crypto.subtle.importKey('pkcs8', raw, RSA, false, ['decrypt']);
    // Verify the private/public pair, not just the claimed public key in the file.
    const probe = crypto.getRandomValues(new Uint8Array(32));
    const pub = await crypto.subtle.importKey('spki', unbase64(expectedPublicKey), RSA, false, ['encrypt']);
    const wrapped = await crypto.subtle.encrypt(RSA, pub, probe);
    const plain = new Uint8Array(await crypto.subtle.decrypt(RSA, privateKey, wrapped));
    if (!probe.every((v, i) => v === plain[i])) throw new Error('Key mismatch');
    return await crypto.subtle.importKey('pkcs8', raw, RSA, false, ['decrypt']);
  } catch { throw new Error('Unable to unlock your key. Check the password and encrypted backup.'); }
  finally { raw?.fill(0); }
}
export async function reencryptIdentity(backup, currentPassword, newPassword, expectedPublicKey) {
  // Validate the backup and key pair before handling exportable key bytes.
  await unlockIdentity(backup, currentPassword, expectedPublicKey);
  const oldKey = await passwordKey(currentPassword, unbase64(backup.salt), backup.iterations, ['decrypt']);
  let raw;
  try {
    raw = new Uint8Array(await crypto.subtle.decrypt({ name: 'AES-GCM', iv: unbase64(backup.iv),
      additionalData: encoder.encode(expectedPublicKey) }, oldKey, unbase64(backup.encrypted)));
    const salt = crypto.getRandomValues(new Uint8Array(16));
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const key = await passwordKey(newPassword, salt, ITERATIONS, ['encrypt']);
    const encrypted = await crypto.subtle.encrypt({ name: 'AES-GCM', iv,
      additionalData: encoder.encode(expectedPublicKey) }, key, raw);
    return { version: 1, publicKey: expectedPublicKey, salt: base64(salt), iv: base64(iv),
      iterations: ITERATIONS, encrypted: base64(encrypted) };
  } finally { raw?.fill(0); }
}
function authenticatedData(message) {
  const version = message.envelopeVersion ?? 1;
  if (version === 1) return encoder.encode(message.clientId);
  if (version !== 2 || !message.conversationId || !message.senderId || !message.recipientId)
    throw new Error('Unsupported or incomplete encrypted envelope.');
  return encoder.encode(JSON.stringify(['cipherchat', 2, message.conversationId, message.senderId, message.recipientId, message.clientId]));
}
export async function encryptMessage(text, senderPublicKey, recipientPublicKey, clientId = crypto.randomUUID(), context = null) {
  const envelope = context ? { envelopeVersion: 2, conversationId: context.conversationId,
    senderId: context.senderId, recipientId: context.recipientId, clientId } : { clientId };
  const aes = await crypto.subtle.generateKey({ name: 'AES-GCM', length: 256 }, true, ['encrypt']);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ciphertext = await crypto.subtle.encrypt({ name: 'AES-GCM', iv, additionalData: authenticatedData(envelope) }, aes, encoder.encode(text));
  const raw = new Uint8Array(await crypto.subtle.exportKey('raw', aes));
  try {
    const wrap = async publicKey => {
      const key = await crypto.subtle.importKey('spki', unbase64(publicKey), RSA, false, ['encrypt']);
      return base64(await crypto.subtle.encrypt(RSA, key, raw));
    };
    return { ...envelope, ciphertext: base64(ciphertext), iv: base64(iv),
      senderKey: await wrap(senderPublicKey), recipientKey: await wrap(recipientPublicKey) };
  } finally { raw.fill(0); }
}
export async function decryptMessage(message, privateKey, ownId) {
  const wrapped = message.senderId === ownId ? message.senderKey : message.recipientKey;
  const raw = new Uint8Array(await crypto.subtle.decrypt(RSA, privateKey, unbase64(wrapped)));
  try {
    const aes = await crypto.subtle.importKey('raw', raw, { name: 'AES-GCM' }, false, ['decrypt']);
    const plaintext = await crypto.subtle.decrypt({ name: 'AES-GCM', iv: unbase64(message.iv),
      additionalData: authenticatedData(message) }, aes, unbase64(message.ciphertext));
    return decoder.decode(plaintext);
  } finally { raw.fill(0); }
}
export async function fingerprint(publicKey) {
  const digest = await crypto.subtle.digest('SHA-256', unbase64(publicKey));
  return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('').match(/.{1,4}/g).join(' ');
}