import React, { useState } from 'react';
import { http, errorText } from '../api.js';

export default function SessionControls({ logout }) {
  const [sessions, setSessions] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function load() {
    setBusy(true); setError('');
    try { setSessions((await http.get('/auth/sessions')).data); }
    catch (e) { setError(errorText(e)); }
    finally { setBusy(false); }
  }
  async function revoke(id) {
    if (!window.confirm(id ? 'Revoke this session’s refresh access?' : 'Revoke refresh access for all sessions and sign out here?')) return;
    setBusy(true); setError('');
    try {
      await http.delete(`/auth/sessions${id ? `/${encodeURIComponent(id)}` : ''}`);
      if (!id) { logout(); return; }
      setSessions(items => items.filter(item => item.id !== id));
    } catch (e) { setError(errorText(e)); }
    finally { setBusy(false); }
  }
  return <details className="privacy-controls"><summary>Active sessions</summary>
    <p className="hint">Revocation prevents token refresh. Existing access tokens and sockets remain usable until their short expiry. Activity is the last sign-in or token refresh, not every request. Session identity is preserved when tokens rotate.</p>
    <button className="secondary" disabled={busy} onClick={load}>{busy ? 'Working…' : 'Refresh session list'}</button>
    {sessions && <ul className="session-list">{sessions.map(s => <li key={s.id}>
      <strong>Session {s.id.slice(0, 8)}</strong>
      <p>Signed in: {new Date(s.createdAt).toLocaleString()}<br />Last refresh: {new Date(s.lastUsedAt).toLocaleString()}<br />Refresh expires: {new Date(s.expiresAt).toLocaleString()}</p>
      <button className="secondary" disabled={busy} onClick={() => revoke(s.id)}>Revoke refresh access</button>
    </li>)}</ul>}
    {sessions?.length === 0 && <p>No refresh sessions remain.</p>}
    <button className="link" disabled={busy} onClick={() => revoke(null)}>Revoke all sessions and sign out</button>
    {error && <p className="error" role="alert">{error}</p>}
  </details>;
}