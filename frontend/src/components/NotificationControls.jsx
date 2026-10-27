import React, { useState } from 'react';
import { enableNotifications, disableNotifications, notificationsEnabled } from '../notifications.js';

export default function NotificationControls({ onError }) {
  const [enabled, setEnabled] = useState(notificationsEnabled);
  async function toggle() {
    try {
      if (enabled) disableNotifications(); else await enableNotifications();
      setEnabled(!enabled);
    } catch (e) { onError(e.message); }
  }
  return <button className="link" onClick={toggle}>{enabled ? 'Disable' : 'Enable'} notifications while open</button>;
}