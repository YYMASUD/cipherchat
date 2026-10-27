let database;
function open() {
  if (!database) database = new Promise((resolve, reject) => {
    const request = indexedDB.open('cipherchat', 3);
    request.onupgradeneeded = () => {
      for (const name of ['identities', 'pins', 'outbox', 'drafts', 'verified'])
        if (!request.result.objectStoreNames.contains(name)) request.result.createObjectStore(name);
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
  return database;
}
async function operation(store, mode, action) {
  const db = await open();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(store, mode);
    const request = action(tx.objectStore(store));
    tx.oncomplete = () => resolve(request.result);
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error ?? new Error('Local storage transaction aborted'));
  });
}
export const loadIdentity = username => operation('identities', 'readonly', s => s.get(username.toLowerCase()));
export const saveIdentity = (username, backup) => operation('identities', 'readwrite', s => s.put(backup, username.toLowerCase()));
export async function pinKey(ownId, peer) {
  const id = `${ownId}:${peer.id}`;
  const previous = await operation('pins', 'readonly', s => s.get(id));
  if (previous && previous !== peer.publicKey) throw new Error(`Security warning: ${peer.username}'s public key changed. Verify their identity before continuing.`);
  if (!previous) await operation('pins', 'readwrite', s => s.put(peer.publicKey, id));
}
export const savePending = item => operation('outbox', 'readwrite', s => s.put(item, item.payload.clientId));
export const removePending = clientId => operation('outbox', 'readwrite', s => s.delete(clientId));
export async function pendingFor(userId) {
  const all = await operation('outbox', 'readonly', s => s.getAll());
  return all.filter(item => item.userId === userId);
}

// Serialize writes per conversation so slower encryption cannot resurrect an older draft.
const draftWrites = new Map();
export function persistDraft(userId, conversationId, createEnvelope) {
  const id = `${userId}:${conversationId}`;
  const next = (draftWrites.get(id) || Promise.resolve()).catch(() => {}).then(async () => {
    if (draftWrites.get(id) !== next) return;
    const envelope = await createEnvelope();
    return operation('drafts', 'readwrite', s => envelope ? s.put(envelope, id) : s.delete(id));
  });
  draftWrites.set(id, next);
  next.finally(() => { if (draftWrites.get(id) === next) draftWrites.delete(id); }).catch(() => {});
  return next;
}
export const verifiedKey = (ownId, peerId) => operation('verified', 'readonly', s => s.get(`${ownId}:${peerId}`));
export const verifyKey = (ownId, peer) => operation('verified', 'readwrite', s => s.put(peer.publicKey, `${ownId}:${peer.id}`));

export async function clearAccountData(user) {
  await Promise.allSettled([...draftWrites.values()]);
  const db = await open();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(['identities', 'pins', 'outbox', 'drafts', 'verified'], 'readwrite');
    tx.objectStore('identities').delete(user.username.toLowerCase());
    for (const name of ['pins', 'drafts', 'verified', 'outbox']) {
      const request = tx.objectStore(name).openCursor();
      request.onsuccess = () => {
        const cursor = request.result;
        if (!cursor) return;
        if (name === 'outbox' ? cursor.value.userId === user.id : String(cursor.key).startsWith(`${user.id}:`)) cursor.delete();
        cursor.continue();
      };
    }
    tx.oncomplete = resolve;
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error ?? new Error('Unable to clear local data'));
  });
}
export async function loadDraft(userId, conversationId) {
  const id = `${userId}:${conversationId}`;
  await draftWrites.get(id);
  return operation('drafts', 'readonly', s => s.get(id));
}