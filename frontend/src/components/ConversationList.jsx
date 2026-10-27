import React from 'react';
import { sortConversations } from '../messageContent.js';

export default function ConversationList({ conversations, selected, select, archived }) {
  const visible = sortConversations(conversations.filter(c => !!c.archived === archived));
  return <nav aria-label={archived ? 'Archived conversations' : 'Conversations'}>
    {!visible.length && <p className="hint">{archived ? 'No archived conversations.' : 'No conversations yet.'}</p>}
    {visible.map(c => <button key={c.id} aria-current={selected?.id === c.id ? 'page' : undefined}
      className={`conversation ${selected?.id === c.id ? 'selected' : ''}`} onClick={() => select(c)}>
      <span>{c.pinned ? '★ ' : ''}@{c.peer.username}{c.muted ? ' · muted' : ''}
        {c.unreadCount > 0 && <span className="badge" aria-label={`${c.unreadCount} unread`}>{c.unreadCount}</span>}</span>
      <small className={c.online ? 'online' : 'offline'}>{c.requestState === 'pending' ? (c.incomingRequest ? 'Message request' : 'Awaiting acceptance') : c.requestState === 'rejected' ? 'Request declined' : c.online ? 'Online' : 'Offline'}</small>
    </button>)}
  </nav>;
}