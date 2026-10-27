ALTER TABLE refresh_tokens ADD COLUMN session_id VARCHAR(64);
ALTER TABLE refresh_tokens ADD COLUMN created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE refresh_tokens ADD COLUMN last_used_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
-- Legacy sessions receive an opaque identifier, never the bearer refresh token.
UPDATE refresh_tokens SET session_id = token_hash;
ALTER TABLE refresh_tokens ALTER COLUMN session_id SET NOT NULL;
CREATE UNIQUE INDEX refresh_session_idx ON refresh_tokens(session_id);