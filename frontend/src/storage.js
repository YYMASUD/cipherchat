let database;
function open() {
  if (!database) database = new Promise((resolve, reject) => {
    const request = indexedDB.open('cipherchat', 1);
    request.onupgradeneeded = () => {
      for (const name of ['identities', 'pins', 'outbox']) request.result.createObjectStore(name);
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