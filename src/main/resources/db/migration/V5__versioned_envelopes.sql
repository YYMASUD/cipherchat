ALTER TABLE chat_messages ADD COLUMN envelope_version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE chat_messages ADD COLUMN recipient_id UUID REFERENCES chat_users(id);
ALTER TABLE chat_messages ADD CONSTRAINT message_envelope_version CHECK (envelope_version IN (1, 2));
ALTER TABLE chat_messages ADD CONSTRAINT message_envelope_recipient CHECK (envelope_version = 1 OR recipient_id IS NOT NULL);