CREATE TABLE chat_users (
    id UUID PRIMARY KEY,
    username VARCHAR(32) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    public_key TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE refresh_tokens (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES chat_users(id),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX refresh_expiry_idx ON refresh_tokens(expires_at);
CREATE TABLE conversations (
    id UUID PRIMARY KEY,
    first_user UUID NOT NULL REFERENCES chat_users(id),
    second_user UUID NOT NULL REFERENCES chat_users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (first_user, second_user),
    CHECK (first_user <> second_user)
);
CREATE INDEX conversation_second_idx ON conversations(second_user);
CREATE TABLE chat_messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversations(id),
    sender_id UUID NOT NULL REFERENCES chat_users(id),
    client_id UUID NOT NULL,
    ciphertext TEXT NOT NULL,
    iv VARCHAR(24) NOT NULL,
    recipient_key VARCHAR(1024) NOT NULL,
    sender_key VARCHAR(1024) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    delivered_at TIMESTAMP WITH TIME ZONE,
    read_at TIMESTAMP WITH TIME ZONE,
    UNIQUE (sender_id, client_id)
);
CREATE INDEX message_history_idx ON chat_messages(conversation_id, created_at DESC, id DESC);