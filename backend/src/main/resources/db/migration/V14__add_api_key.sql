-- API Key table for MCP client authentication
-- API Key는 만료 없는 인증 방식으로 MCP 클라이언트(Claude Desktop, Claude Code)용

CREATE TABLE dm_api_key (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES dm_user(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    key_prefix VARCHAR(20) NOT NULL,
    key_hash VARCHAR(64) NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT uq_api_key_user_name UNIQUE (user_id, name),
    CONSTRAINT uq_api_key_hash UNIQUE (key_hash)
);

-- Indexes for performance
CREATE INDEX idx_api_key_user_id ON dm_api_key(user_id);
CREATE INDEX idx_api_key_hash ON dm_api_key(key_hash);
CREATE INDEX idx_api_key_active ON dm_api_key(active) WHERE active = TRUE;

-- Comments
COMMENT ON TABLE dm_api_key IS 'API Keys for MCP client authentication (never expires unless manually revoked)';
COMMENT ON COLUMN dm_api_key.name IS 'User-defined name for identification (e.g., "Claude Desktop")';
COMMENT ON COLUMN dm_api_key.key_prefix IS 'Display prefix (first 8 chars): docst_ak_xxxxxxxx...';
COMMENT ON COLUMN dm_api_key.key_hash IS 'SHA-256 hash of full API key (64 hex chars)';
COMMENT ON COLUMN dm_api_key.last_used_at IS 'Last authentication timestamp';
COMMENT ON COLUMN dm_api_key.expires_at IS 'Optional expiration (NULL = never expires)';
