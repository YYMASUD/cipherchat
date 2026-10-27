import React, { useEffect, useState } from 'react';
import { http, errorText } from '../api.js';

export default function PrivacyControls({ onError }) {
  const [settings, setSettings] = useState(null);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    let active = true;
    http.get('/privacy').then(({ data }) => { if (active) setSettings(data); }).catch(e => { if (active) onError(errorText(e)); });
    return () => { active = false; };
  }, []);
  async function toggle(key) {
    setBusy(true);
    try { setSettings((await http.post('/privacy', { ...settings, [key]: !settings[key] })).data); }
    catch (e) { onError(errorText(e)); }
    finally { setBusy(false); }
  }
  return <details className="privacy-controls"><summary>Privacy settings</summary>
    {!settings && <p>Loading settings…</p>}
    {settings && Object.entries({ showPresence: 'Show online status', sendTyping: 'Send typing indicators', sendReadReceipts: 'Send read receipts', requireMessageRequests: 'Require requests from new contacts' })
      .map(([key, label]) => <label key={key}><input type="checkbox" checked={settings[key]} disabled={busy} onChange={() => toggle(key)} /> {label}</label>)}
    <p className="hint">Previously sent receipts cannot be withdrawn. Delivery acknowledgements remain enabled.</p>
    <p className="hint">Message requests apply to new conversations only. New contacts may send one encrypted introduction before you accept.</p>
  </details>;
}