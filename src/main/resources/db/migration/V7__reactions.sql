CREATE TABLE message_reactions (
    id VARCHAR(200) PRIMARY KEY,
    message_id UUID NOT NULL REFERENCES chat_messages(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES chat_users(id) ON DELETE CASCADE,
    emoji VARCHAR(8) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (message_id, user_id, emoji)
);
CREATE INDEX reaction_message_idx ON message_reactions(message_id);