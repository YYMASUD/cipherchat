import React, { useLayoutEffect, useRef, useState } from 'react';
import { http, errorText } from '../api.js';

const EMOJIS = ['\uD83D\uDC4D', '\u2764\uFE0F', '\uD83D\uDE02', '\uD83D\uDE2E', '\uD83D\uDE22', '\uD83D\uDD25', '\u2705'];

function ReactionPicker({ messageId, onReact }) {
  const [open, setOpen] = useState(false);
  return (
    <span className="reaction-picker-wrap">
      <button className="link reaction-add-btn" title="React" onClick={() => setOpen(o => !o)}>+</button>
      {open && (
        <span className="reaction-picker" role="menu">
          {EMOJIS.map(e => (
            <button key={e} className="emoji-btn" role="menuitem"
              onClick={() => { onReact(messageId, e); setOpen(false); }}>{e}</button>
          ))}
        </span>
      )}
    </span>
  );
}

function ReactionBar({ reactions, ownId, messageId, onReact }) {
  if (!reactions || reactions.length === 0) return null;
  const grouped = {};
  for (const r of reactions) {
    if (!grouped[r.emoji]) grouped[r.emoji] = { count: 0, reacted: false, users: [] };
    grouped[r.emoji].count++;
    grouped[r.emoji].users.push(r.username);
    if (r.userId === ownId) grouped[r.emoji].reacted = true;
  }
  return (
    <div className="reaction-bar">
      {Object.entries(grouped).map(([emoji, { count, reacted, users }]) => (
        <button key={emoji} className={`reaction-chip${reacted ? ' own-reaction' : ''}`}
          title={users.join(', ')} onClick={() => onReact(messageId, emoji)}>
          {emoji} {count}
        </button>
      ))}
    </div>
  );
}

export default function MessageList({ messages, ownId, hasMore, loadOlder, reply, onDelete, onReact, unreadCount }) {
  const viewport = useRef(null);
  const previous = useRef({ first: null, last: null, height: 0, count: 0 });
  const loading = useRef(false);
  const [busy, setBusy] = useState(false);
  const [copyError, setCopyError] = useState('');

  // Compute index of first unread message for the divider
  const firstUnreadIndex = unreadCount > 0 ? Math.max(0, messages.length - unreadCount) : -1;

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

  return (
    <div className="messages" ref={viewport} onScroll={() => {
      if (viewport.current.scrollTop < 60) void older();
    }} aria-label="Message history">
      {hasMore && <button className="secondary" disabled={busy} onClick={older}>{busy ? 'Loading\u2026' : 'Load older messages'}</button>}
      {!messages.length && <p className="empty">No messages yet. Say hello privately.</p>}
      {copyError && <p role="alert" className="error">{copyError}</p>}
      {messages.map((m, i) => (
        <React.Fragment key={m.id}>
          {i === firstUnreadIndex && (
            <div className="unread-marker" role="status" aria-label="New messages below">
              <span>New messages</span>
            </div>
          )}
          <article id={`message-${m.id}`} tabIndex={-1}
            className={`bubble ${m.senderId === ownId ? 'own' : ''} ${m.invalid ? 'invalid' : ''} ${m.deletedAt ? 'deleted' : ''}`}>
            {m.reply && <button className="reply-preview" onClick={() => {
              const target = document.getElementById(`message-${m.reply.id}`);
              if (target) { target.scrollIntoView({ block: 'center' }); target.focus(); }
            }} title="Jump to reply if loaded">{'\u21AA'} {m.reply.text}</button>}
            {m.deletedAt
              ? <p className="deleted-text"><em>Message deleted.</em></p>
              : <p>{m.text}</p>
            }
            <small>
              {new Date(m.createdAt).toLocaleString()}
              {m.senderId === ownId && ` \u00B7 ${m.readAt ? 'Read' : m.deliveredAt ? 'Delivered' : 'Sent'}`}
              {m.deletedAt && ' \u00B7 Deleted'}
            </small>
            {!m.deletedAt && <ReactionBar reactions={m.reactions} ownId={ownId} messageId={m.id} onReact={onReact} />}
            {!m.invalid && !m.deletedAt && (
              <div className="message-actions">
                <button className="link" onClick={() => reply(m)}>Reply</button>
                <button className="link" onClick={async () => {
                  try { await navigator.clipboard.writeText(m.text); setCopyError(''); }
                  catch { setCopyError('Clipboard access unavailable. Select and copy the message text manually.'); }
                }}>Copy text</button>
                <ReactionPicker messageId={m.id} onReact={onReact} />
                {m.senderId === ownId && (
                  <button className="link danger" onClick={() => onDelete && onDelete(m)}>Delete</button>
                )}
              </div>
            )}
          </article>
        </React.Fragment>
      ))}
    </div>
  );
}