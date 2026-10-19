import React, { useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import { http, setSession, currentSession, refreshSession, onExpired, signOut, errorText } from './api.js';
import { generateIdentity, unlockIdentity, encryptMessage, decryptMessage, fingerprint } from './crypto.js';
import { loadIdentity, saveIdentity, pinKey, savePending, removePending, pendingFor } from './storage.js';

function Auth({ authenticated }) {
  const [register, setRegister] = useState(false);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [backup, setBackup] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  async function submit(event) {
    event.preventDefault(); setError(''); setBusy(true);
    let tokens;
    try {
      if (!crypto.subtle) throw new Error('Web Crypto requires HTTPS or localhost.');
      if (new TextEncoder().encode(password).length > 72) throw new Error('Password must be at most 72 UTF-8 bytes.');
      const name = username.toLowerCase();
      let identity;
      if (register) {
        const existing = await loadIdentity(name);
        identity = existing
          ? { privateKey: await unlockIdentity(existing, password, existing.publicKey), publicKey: existing.publicKey, backup: existing }
          : await generateIdentity(password);
        // Persist before registration: a network failure must never discard a registered private key.
        await saveIdentity(name, identity.backup);
        tokens = (await http.post('/auth/register', { username: name, password, publicKey: identity.publicKey })).data;
      } else {
        tokens = (await http.post('/auth/login', { username: name, password })).data;
        const saved = backup || await loadIdentity(name);
        if (!saved) throw new Error('No private key on this browser. Import your encrypted key backup, then sign in again. Keys cannot be recovered from the server.');
        identity = { privateKey: await unlockIdentity(saved, password, tokens.user.publicKey), backup: saved };
        await saveIdentity(name, saved);
      }
      setSession(tokens);
      setPassword('');
      authenticated({ user: tokens.user, privateKey: identity.privateKey, backup: identity.backup });
    } catch (e) {
      if (tokens) await http.post('/auth/logout', { refreshToken: tokens.refreshToken }).catch(() => {});
      setError(errorText(e));
    } finally { setBusy(false); }
  }
  async function importBackup(event) {
    try {
      const file = event.target.files[0];
      if (!file) return;
      if (file.size > 30000) throw new Error('Backup file is too large.');
      setBackup(JSON.parse(await file.text())); setError('');
    } catch (e) { setError(errorText(e)); }
  }
  return <main className="auth-shell"><section className="auth-card">
    <div className="brand">◈ CipherChat</div>
    <h1>{register ? 'Start a private conversation.' : 'Welcome back.'}</h1>
    <p>End-to-end encrypted. Your private key stays on your device.</p>
    <form onSubmit={submit}>
      <label>Username<input value={username} onChange={e => setUsername(e.target.value)} pattern="[a-zA-Z0-9_]{3,32}" required autoComplete="username" maxLength={32} /></label>
      <label>Password<input type="password" value={password} onChange={e => setPassword(e.target.value)} required minLength={register ? 12 : 1} maxLength={72} autoComplete={register ? 'new-password' : 'current-password'} /></label>
      {register && <p className="notice">Use at least 12 characters. After signing up, download your encrypted key backup. Losing your browser data without a backup makes your history unrecoverable.</p>}
      {!register && <label className="file-label">Restore encrypted key backup (new browser)<input type="file" accept="application/json,.json" onChange={importBackup} />{backup && <small>Backup loaded; password verification happens on sign in.</small>}</label>}
      {error && <p className="error" role="alert">{error}</p>}
      <button disabled={busy}>{busy ? 'Securing your session…' : register ? 'Create account' : 'Sign in & unlock'}</button>
    </form>
    <button className="link" disabled={busy} onClick={() => { setRegister(!register); setError(''); }}>{register ? 'Already have an account? Sign in' : 'New here? Create an account'}</button>
  </section></main>;
}

function Chat({ identity, logout }) {
  const { user, privateKey, backup } = identity;
  const [conversations, setConversations] = useState([]);
  const [selected, setSelected] = useState(null);
  const [messages, setMessages] = useState([]);
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [draft, setDraft] = useState('');
  const [error, setError] = useState('');
  const [connected, setConnected] = useState(false);
  const [typing, setTyping] = useState(false);
  const [busy, setBusy] = useState(false);
  const [hasMore, setHasMore] = useState(false);
  const [page, setPage] = useState(0);
  const [queued, setQueued] = useState(0);
  const [peerFingerprint, setPeerFingerprint] = useState('');
  const [ownFingerprint, setOwnFingerprint] = useState('');
  const selectedRef = useRef(null);
  const clientRef = useRef(null);
  const typingTimer = useRef(null);
  const typingSent = useRef(0);
  const active = useRef(true);
  const syncing = useRef(false);
  const bottom = useRef(null);
  const historyVersion = useRef(0);
  const pageRef = useRef(0);

  function merge(items) {
    if (!active.current) return;
    setMessages(old => {
      const merged = new Map(old.map(m => [m.id, m]));
      for (const m of items) {
        const previous = merged.get(m.id);
        merged.set(m.id, { ...m, deliveredAt: m.deliveredAt || previous?.deliveredAt, readAt: m.readAt || previous?.readAt });
      }
      return [...merged.values()].sort((a, b) => a.createdAt.localeCompare(b.createdAt) || a.id.localeCompare(b.id));
    });
  }
  async function readable(m) {
    try { return { ...m, text: await decryptMessage(m, privateKey, user.id) }; }
    catch { return { ...m, text: 'Unable to decrypt or authenticate this message.', invalid: true }; }
  }
  async function acknowledge(m, read) {
    if (m.senderId !== user.id && (!m.deliveredAt || (read && !m.readAt)))
      await http.post('/conversations/receipts', { messageId: m.id, read });
  }
  async function loadConversations() {
    const all = [];
    for (let n = 0; ; n++) {
      const { data } = await http.get(`/conversations?page=${n}`);
      all.push(...data);
      if (data.length < 50) break;
    }
    if (active.current) setConversations(all);
    return all;
  }
  async function loadHistory(conversation, historyPage = 0) {
    const version = historyVersion.current;
    const { data } = await http.get(`/conversations/${conversation.id}/messages?page=${historyPage}`);
    const decoded = await Promise.all(data.messages.map(readable));
    if (!active.current || selectedRef.current?.id !== conversation.id || version !== historyVersion.current) return;
    merge(decoded);
    if (historyPage >= pageRef.current) {
      pageRef.current = historyPage; setHasMore(data.hasMore); setPage(historyPage);
    }
    await Promise.all(data.messages.map(m => acknowledge(m, document.visibilityState === 'visible')));
  }
  async function select(conversation) {
    try {
      await pinKey(user.id, conversation.peer);
      const print = await fingerprint(conversation.peer.publicKey);
      historyVersion.current++;
      pageRef.current = 0;
      selectedRef.current = conversation;
      setSelected(conversation); setPeerFingerprint(print); setMessages([]); setDraft(''); setTyping(false); setError(''); setHasMore(false);
      await loadHistory(conversation);
    } catch (e) { setError(errorText(e)); }
  }
  async function syncOutbox() {
    if (syncing.current) return;
    syncing.current = true;
    try {
      for (const item of await pendingFor(user.id)) {
        const { data } = await http.post(`/conversations/${item.conversationId}/messages`, item.payload);
        await removePending(item.payload.clientId);
        const decoded = await readable(data);
        if (selectedRef.current?.id === item.conversationId) merge([decoded]);
      }
    } catch (e) { if (active.current) setError(`Encrypted message queued. ${errorText(e)}`); }
    finally {
      syncing.current = false;
      const count = (await pendingFor(user.id)).length;
      if (active.current) setQueued(count);
    }
  }
  useEffect(() => {
    active.current = true;
    void fingerprint(user.publicKey).then(setOwnFingerprint);
    let cancelled = false;
    const client = new Client({
      brokerURL: `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws`,
      reconnectDelay: 4000, heartbeatIncoming: 0, heartbeatOutgoing: 10000,
      beforeConnect: async () => {
        const session = currentSession();
        if (!session) return;
        const claims = JSON.parse(atob(session.accessToken.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
        if (claims.exp * 1000 < Date.now() + 30000) await refreshSession();
        client.connectHeaders = { Authorization: `Bearer ${currentSession().accessToken}` };
      },
      onConnect: () => {
        if (cancelled) return;
        setConnected(true);
        client.subscribe('/user/queue/events', frame => {
          const event = JSON.parse(frame.body);
          void (async () => {
            if (event.type === 'message' || event.type === 'receipt') {
              const m = event.data;
              if (selectedRef.current?.id === m.conversationId) {
                const decoded = await readable(m);
                if (selectedRef.current?.id === m.conversationId && active.current) merge([decoded]);
              }
              if (event.type === 'message') {
                await acknowledge(m, selectedRef.current?.id === m.conversationId && document.visibilityState === 'visible');
                await loadConversations();
              }
            } else if (event.type === 'conversation') await loadConversations();
            else if (event.type === 'typing' && event.data.conversationId === selectedRef.current?.id) {
              setTyping(event.data.typing); clearTimeout(typingTimer.current);
              typingTimer.current = setTimeout(() => setTyping(false), 4000);
            } else if (event.type === 'error') setError(event.data.message);
          })().catch(e => { if (active.current) setError(errorText(e)); });
        });
        // The database, not the socket, is the source of truth after reconnect.
        void loadConversations().then(() => selectedRef.current && loadHistory(selectedRef.current)).then(syncOutbox).catch(e => setError(errorText(e)));
      },
      onWebSocketClose: () => { if (!cancelled) setConnected(false); },
      onStompError: () => { if (!cancelled) setConnected(false); },
    });
    clientRef.current = client;
    client.activate();
    const refresh = () => {
      if (document.visibilityState === 'visible') {
        void loadConversations().catch(e => active.current && setError(errorText(e)));
        if (selectedRef.current) void loadHistory(selectedRef.current).catch(e => active.current && setError(errorText(e)));
        void syncOutbox();
      }
    };
    refresh();
    const interval = setInterval(refresh, 15000);
    document.addEventListener('visibilitychange', refresh);
    window.addEventListener('online', refresh);
    return () => {
      cancelled = true; active.current = false; clearInterval(interval); clearTimeout(typingTimer.current);
      document.removeEventListener('visibilitychange', refresh); window.removeEventListener('online', refresh);
      void client.deactivate();
    };
  }, []);
  useEffect(() => {
    let cancelled = false;
    const timer = setTimeout(() => {
      if (!query.trim()) { setResults([]); return; }
      http.get('/users', { params: { q: query.trim() } }).then(({ data }) => { if (!cancelled) setResults(data); })
        .catch(e => { if (!cancelled) setError(errorText(e)); });
    }, 300);
    return () => { cancelled = true; clearTimeout(timer); };
  }, [query]);
  useEffect(() => { bottom.current?.scrollIntoView({ behavior: 'smooth' }); }, [messages.length]);
  async function start(peer) {
    try {
      await pinKey(user.id, peer);
      const { data } = await http.post('/conversations', { userId: peer.id });
      await loadConversations(); setQuery(''); setResults([]); await select(data);
    } catch (e) { setError(errorText(e)); }
  }
  async function send(event) {
    event.preventDefault();
    if (!draft.trim() || !selected || busy) return;
    setBusy(true); setError('');
    try {
      const conversation = selected;
      await pinKey(user.id, conversation.peer);
      const payload = await encryptMessage(draft, user.publicKey, conversation.peer.publicKey);
      await savePending({ userId: user.id, conversationId: conversation.id, payload });
      setDraft('');
      if (clientRef.current?.connected) clientRef.current.publish({ destination: '/app/typing', body: JSON.stringify({ conversationId: conversation.id, typing: false }) });
      await syncOutbox();
    } catch (e) { setError(errorText(e)); }
    finally { setBusy(false); }
  }
  function changeDraft(value) {
    setDraft(value);
    if (clientRef.current?.connected && selected && Date.now() - typingSent.current > 2000) {
      typingSent.current = Date.now();
      clientRef.current.publish({ destination: '/app/typing', body: JSON.stringify({ conversationId: selected.id, typing: !!value }) });
    }
  }
  function downloadBackup() {
    const url = URL.createObjectURL(new Blob([JSON.stringify(backup, null, 2)], { type: 'application/json' }));
    const a = document.createElement('a'); a.href = url; a.download = `cipherchat-${user.username}-encrypted-key.json`; a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
  return <main className="chat-shell">
    <aside className="sidebar">
      <div className="brand">◈ CipherChat</div>
      <div className="account"><strong>@{user.username}</strong><span className={connected ? 'online' : 'offline'}>{connected ? '● Connected' : '○ Reconnecting'}</span></div>
      <button className="secondary" onClick={downloadBackup}>Download encrypted key backup</button>
      <p className="hint">Keep a backup before clearing browser data. Your password alone cannot recover your key.</p>
      <label>New conversation<input placeholder="Search username…" value={query} onChange={e => setQuery(e.target.value)} maxLength={32} /></label>
      {results.map(peer => <button className="conversation" key={peer.id} onClick={() => start(peer)}>+ @{peer.username}</button>)}
      {query && results.length === 0 && <p className="hint">No matching users.</p>}
      <nav aria-label="Conversations">{conversations.map(c => <button key={c.id} className={`conversation ${selected?.id === c.id ? 'selected' : ''}`} onClick={() => select(c)}>
        <span>@{c.peer.username}</span><small className={c.online ? 'online' : 'offline'}>{c.online ? 'Online' : 'Offline'}</small>
      </button>)}</nav>
      <button className="link logout" onClick={logout}>Sign out & lock keys</button>
    </aside>
    <section className="chat-panel">
      {error && <div className="error banner" role="alert">{error}<button className="link" onClick={() => setError('')}>Dismiss</button></div>}
      {queued > 0 && <div className="notice banner">{queued} encrypted message(s) queued. <button className="link" onClick={syncOutbox}>Retry sending</button></div>}
      {selected ? <>
        <header><h2>@{selected.peer.username}</h2><details><summary>End-to-end encrypted · verify keys</summary><p>Contact's key — compare over a trusted channel:</p><code>{peerFingerprint}</code><p>Your key — share this fingerprint with your contact:</p><code>{ownFingerprint}</code><p>Trust-on-first-use blocks changes on this browser. No forward secrecy.</p></details></header>
        <div className="messages" aria-live="polite">
          {hasMore && <button className="secondary" onClick={() => loadHistory(selected, page + 1).catch(e => setError(errorText(e)))}>Load older messages</button>}
          {messages.length === 0 && <p className="empty">No messages yet. Say hello privately.</p>}
          {messages.map(m => <article key={m.id} className={`bubble ${m.senderId === user.id ? 'own' : ''} ${m.invalid ? 'invalid' : ''}`}>
            <p>{m.text}</p><small>{new Date(m.createdAt).toLocaleString()}{m.senderId === user.id && ` · ${m.readAt ? 'Read' : m.deliveredAt ? 'Delivered' : 'Sent'}`}</small>
          </article>)}<div ref={bottom} />
        </div>
        <div className="typing">{typing ? `${selected.peer.username} is typing…` : '\u00a0'}</div>
        <form className="composer" onSubmit={send}>
          <textarea aria-label="Message" placeholder="Write an encrypted message…" value={draft} onChange={e => changeDraft(e.target.value)} maxLength={8000} rows={2}
            onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); void send(e); } }} />
          <button disabled={busy || !draft.trim()}>{busy ? 'Encrypting…' : 'Send'}</button>
        </form>
      </> : <div className="welcome"><h1>Your conversations, private.</h1><p>Search for a user or select a conversation to begin.</p><p>Messages are encrypted in your browser. The server stores ciphertext only.</p></div>}
    </section>
  </main>;
}

export default function App() {
  const [identity, setIdentity] = useState(null);
  const [notice, setNotice] = useState('');
  useEffect(() => {
    onExpired(() => { setIdentity(null); setNotice('Session expired. Sign in to unlock your keys again.'); });
    return () => onExpired(() => {});
  }, []);
  function logout() { setIdentity(null); void signOut(); }
  return <>{notice && !identity && <div className="notice banner">{notice}</div>}{identity
    ? <Chat identity={identity} logout={logout} />
    : <Auth authenticated={value => { setNotice(''); setIdentity(value); }} />}</>;
}