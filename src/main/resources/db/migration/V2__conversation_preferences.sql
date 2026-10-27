CREATE TABLE conversation_preferences (
    id VARCHAR(73) PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES chat_users(id) ON DELETE CASCADE,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    muted BOOLEAN NOT NULL DEFAULT FALSE,
    last_read_at TIMESTAMP WITH TIME ZONE,
    last_read_id UUID,
    UNIQUE (conversation_id, user_id)
);
CREATE INDEX unread_message_idx ON chat_messages(conversation_id, sender_id, created_at, id);