// A tagged encrypted payload preserves compatibility with existing plain-text messages.
const PREFIX = 'cipherchat:content:v1:';
export function encodeContent(text, reply = null) {
  return PREFIX + JSON.stringify({ text, reply: reply ? { id: reply.id, text: reply.text.slice(0, 240) } : null });
}
export function decodeContent(value) {
  if (!value.startsWith(PREFIX)) return { text: value, reply: null };
  const content = JSON.parse(value.slice(PREFIX.length));
  if (typeof content.text !== 'string' || (content.reply !== null &&
      (!content.reply || typeof content.reply.id !== 'string' || typeof content.reply.text !== 'string')))
    throw new Error('Invalid encrypted content');
  return content;
}
export function sortConversations(items) {
  return [...items].sort((a, b) => Number(b.pinned) - Number(a.pinned) ||
    b.lastActivityAt.localeCompare(a.lastActivityAt) || a.id.localeCompare(b.id));
}