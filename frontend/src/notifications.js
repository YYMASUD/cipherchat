export function notificationsEnabled() {
  try { return localStorage.getItem('cipherchat-notifications') === 'enabled'; }
  catch { return false; }
}
export async function enableNotifications() {
  if (!('Notification' in window)) throw new Error('This browser does not support notifications.');
  const permission = await Notification.requestPermission();
  if (permission !== 'granted') throw new Error('Notification permission was not granted.');
  localStorage.setItem('cipherchat-notifications', 'enabled');
}
export function disableNotifications() { localStorage.removeItem('cipherchat-notifications'); }
export function notifyMessage(message, conversation, ownId) {
  if (message.senderId === ownId || conversation?.muted || conversation?.blocked ||
      document.visibilityState === 'visible' || !notificationsEnabled() ||
      !('Notification' in window) || Notification.permission !== 'granted') return;
  // No plaintext or contact name is exposed on the lock screen.
  try {
    const notification = new Notification('CipherChat', { body: 'You have a new encrypted message.', tag: message.id });
    notification.onclick = () => { window.focus(); notification.close(); };
  } catch { /* Some mobile browsers require service-worker notifications instead. */ }
}