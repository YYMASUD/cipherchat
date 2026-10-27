import { test } from 'node:test';
import assert from 'node:assert/strict';
import { notificationsEnabled, enableNotifications, disableNotifications, notifyMessage } from '../src/notifications.js';

test('notifications are generic, opt-in, and respect mute and visibility', async () => {
  const originals = Object.fromEntries(['window', 'document', 'localStorage', 'Notification'].map(key => [key, Object.getOwnPropertyDescriptor(globalThis, key)]));
  try {
    const store = new Map(), sent = [];
    class MockNotification {
      static permission = 'granted';
      static async requestPermission() { return this.permission; }
      constructor(title, options) { sent.push({ title, options }); }
    }
    const mocks = {
      window: { Notification: MockNotification }, document: { visibilityState: 'hidden' }, Notification: MockNotification,
      localStorage: { getItem: key => store.get(key), setItem: (key, value) => store.set(key, value), removeItem: key => store.delete(key) },
    };
    for (const [key, value] of Object.entries(mocks)) Object.defineProperty(globalThis, key, { configurable: true, writable: true, value });
    const m = { id: 'id', senderId: 'peer', text: 'secret text' };
    assert.equal(notificationsEnabled(), false);
    notifyMessage(m, {}, 'own'); assert.equal(sent.length, 0);
    await enableNotifications();
    notifyMessage(m, { muted: true }, 'own');
    notifyMessage(m, { blocked: true }, 'own');
    notifyMessage({ ...m, senderId: 'own' }, {}, 'own');
    assert.equal(sent.length, 0);
    notifyMessage(m, {}, 'own');
    assert.equal(sent.length, 1);
    assert.equal(JSON.stringify(sent).includes('secret text'), false);
    document.visibilityState = 'visible'; notifyMessage(m, {}, 'own');
    assert.equal(sent.length, 1);
    disableNotifications(); assert.equal(notificationsEnabled(), false);
  } finally {
    for (const [key, descriptor] of Object.entries(originals)) {
      if (descriptor) Object.defineProperty(globalThis, key, descriptor); else delete globalThis[key];
    }
  }
});