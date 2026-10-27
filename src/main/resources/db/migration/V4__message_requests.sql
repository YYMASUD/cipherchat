ALTER TABLE privacy_settings ADD COLUMN require_message_requests BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE conversations ADD COLUMN request_recipient UUID REFERENCES chat_users(id);
ALTER TABLE conversations ADD COLUMN request_state VARCHAR(16) NOT NULL DEFAULT 'accepted';
ALTER TABLE conversations ADD CONSTRAINT conversation_request_state CHECK (request_state IN ('accepted', 'pending', 'rejected'));