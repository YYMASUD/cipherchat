import React, { useLayoutEffect, useRef } from 'react';

export default function MessageList({ messages, ownId, hasMore, loadOlder, reply }) {
  const viewport = useRef(null);
  const previous = useRef({ first: null, last: null, height: 0, count: 0 });
  const loading = useRef(false);
  const [busy, setBusy] = React.useState(false);
  const [copyError, setCopyError] = React.useState('');
  useLayoutEffect(() => {
    const node = viewport.current;
    const old = previous.current;
    const first = messages[0]?.id, last = messages.at(-1)?.id;
    const prepended = old.first && first !== old.first && messages.some(m => m.id === old.first);
    if (prepended) node.scrollTop += node.scrollHeight - old.height;
    else if (!old.count || (last !== old.last && node.scrollHeight - node.scrollTop - node.clientHeight < 350))
      node.scrollTop = node.scrollHeight;
    previous.current = { first, last, height: node.scrollHeight, count: messages.length };
  }, [messages]);
  async function older() {
    if (!hasMore || loading.current) return;
    loading.current = true; setBusy(true);
    const node = viewport.current;
    previous.current.height = node.scrollHeight;
    try { await loadOlder(); }
    finally { loading.current = false; setBusy(false); }
  }
  return <div className="messages" ref={viewport} onScroll={() => {
    if (viewport.current.scrollTop < 60) void older();
  }} aria-label="Message history">
    {hasMore && <button className="secondary" disabled={busy} onClick={older}>{busy ? 'Loading…' : 'Load older messages'}</button>}
    {!messages.length && <p className="empty">No messages yet. Say hello privately.</p>}
    {copyError && <p role="alert" className="error">{copyError}</p>}
    {messages.map(m => <article id={`message-${m.id}`} key={m.id} tabIndex={-1}
      className={`bubble ${m.senderId === ownId ? 'own' : ''} ${m.invalid ? 'invalid' : ''}`}>
      {m.reply && <button className="reply-preview" onClick={() => {
        const target = document.getElementById(`message-${m.reply.id}`);
        if (target) { target.scrollIntoView({ block: 'center' }); target.focus(); }
      }} title="Jump to reply if loaded">↪ {m.reply.text}</button>}
      <p>{m.text}</p><small>{new Date(m.createdAt).toLocaleString()}{m.senderId === ownId && ` · ${m.readAt ? 'Read' : m.deliveredAt ? 'Delivered' : 'Sent'}`}</small>
      {!m.invalid && <div className="message-actions"><button className="link" onClick={() => reply(m)}>Reply</button>
        <button className="link" onClick={async () => {
          try { await navigator.clipboard.writeText(m.text); setCopyError(''); }
          catch { setCopyError('Clipboard access unavailable. Select and copy the message text manually.'); }
        }}>Copy text</button></div>}
    </article>)}
  </div>;
}