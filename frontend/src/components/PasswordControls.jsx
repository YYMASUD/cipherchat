import React, { useState } from 'react';
import { http, errorText } from '../api.js';
import { reencryptIdentity } from '../crypto.js';
import { saveIdentity } from '../storage.js';

export default function PasswordControls({ user, backup, logout }) {
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [prepared, setPrepared] = useState(null);
  const [busy, setBusy] = useState(false);
  const [downloaded, setDownloaded] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState('');
  async function prepare(event) {
    event.preventDefault(); setBusy(true); setError('');
    try {
      if (next !== confirmation) throw new Error('New passwords do not match.');
      if (next === current) throw new Error('Choose a different password.');
      if (next.length < 12 || new TextEncoder().encode(next).length > 72)
        throw new Error('Use at least 12 characters and at most 72 UTF-8 bytes.');
      setPrepared(await reencryptIdentity(backup, current, next, user.publicKey));
    } catch (e) { setError(errorText(e)); }
    finally { setBusy(false); }
  }
  function download() {
    const url = URL.createObjectURL(new Blob([JSON.stringify(prepared)], { type: 'application/json' }));
    const a = document.createElement('a'); a.href = url; a.download = `cipherchat-${user.username}-new-password-key.json`; a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000); setDownloaded(true);
  }
  async function apply() {
    setBusy(true); setError('');
    try {
      await http.post('/auth/password', { currentPassword: current, newPassword: next });
      setDone(true); setCurrent(''); setNext(''); setConfirmation('');
      try { await saveIdentity(user.username, prepared); }
      catch { setError('Password changed, but local backup storage failed. Import the downloaded NEW backup when signing in.'); }
    } catch (e) {
      setError(`${errorText(e)} If the connection failed, keep BOTH backups and check which password works before retrying. No encryption keys were rotated.`);
    } finally { setBusy(false); }
  }
  return <details className="privacy-controls"><summary>Change password</summary>
    <p className="hint">Your encryption identity stays the same. First download a new encrypted backup. Keep your old backup until the change succeeds.</p>
    {!prepared && <form onSubmit={prepare}>
      <label>Current password<input type="password" autoComplete="current-password" value={current} disabled={busy} onChange={e => setCurrent(e.target.value)} required maxLength={72} /></label>
      <label>New password<input type="password" autoComplete="new-password" value={next} disabled={busy} onChange={e => setNext(e.target.value)} required minLength={12} maxLength={72} /></label>
      <label>Confirm new password<input type="password" autoComplete="new-password" value={confirmation} disabled={busy} onChange={e => setConfirmation(e.target.value)} required /></label>
      <button disabled={busy}>{busy ? 'Re-encrypting…' : 'Prepare new backup'}</button>
    </form>}
    {prepared && <><button className="secondary" onClick={download}>Download new key backup</button>
      {!done && <button disabled={!downloaded || busy} onClick={apply}>{busy ? 'Changing…' : 'Apply password change'}</button>}
      {!done && <button className="link" disabled={busy} onClick={() => { setPrepared(null); setDownloaded(false); }}>Cancel</button>}</>}
    {done && <><p role="status">Password changed. Refresh tokens on all devices were revoked. Existing access tokens and sockets expire within the configured access-token lifetime; this is not immediate session revocation. Replace old exported backups and update other browsers using your new backup.</p><button onClick={logout}>Sign out and unlock again</button></>}
    {error && <p className="error" role="alert">{error}</p>}
  </details>;
}