-- Credential 테이블 생성
CREATE TABLE dm_credential (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES dm_user(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    username VARCHAR(255),
    encrypted_secret TEXT NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_credential_user_name UNIQUE (user_id, name)
);

-- Repository에 credential_id 컬럼 추가
ALTER TABLE dm_repository ADD COLUMN credential_id UUID REFERENCES dm_credential(id) ON DELETE SET NULL;

-- 인덱스 추가
CREATE INDEX idx_credential_user_id ON dm_credential(user_id);
CREATE INDEX idx_credential_type ON dm_credential(type);
CREATE INDEX idx_repository_credential_id ON dm_repository(credential_id);
