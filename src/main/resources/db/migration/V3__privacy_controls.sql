CREATE TABLE privacy_settings (
    user_id UUID PRIMARY KEY REFERENCES chat_users(id) ON DELETE CASCADE,
    show_presence BOOLEAN NOT NULL DEFAULT TRUE,
    send_typing BOOLEAN NOT NULL DEFAULT TRUE,
    send_read_receipts BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE blocked_users (
    id VARCHAR(73) PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES chat_users(id) ON DELETE CASCADE,
    blocked_id UUID NOT NULL REFERENCES chat_users(id) ON DELETE CASCADE,
    UNIQUE (user_id, blocked_id),
    CHECK (user_id <> blocked_id)
);
CREATE INDEX blocked_reverse_idx ON blocked_users(blocked_id, user_id);