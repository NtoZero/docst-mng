-- V8: Add Password Authentication
-- Add password authentication support with Argon2id hashing

-- Add password_hash column to dm_user table
ALTER TABLE dm_user
ADD COLUMN password_hash VARCHAR(150);

-- Add index for email-based lookups (LOCAL provider only)
CREATE INDEX idx_user_email ON dm_user(email) WHERE provider = 'LOCAL';

-- Delete existing LOCAL users (data loss acceptable as per requirement)
DELETE FROM dm_user WHERE provider = 'LOCAL';

-- Add comment
COMMENT ON COLUMN dm_user.password_hash IS 'Argon2id password hash for LOCAL users only. Format: $argon2id$v=19$m=19456,t=2,p=1$...';
