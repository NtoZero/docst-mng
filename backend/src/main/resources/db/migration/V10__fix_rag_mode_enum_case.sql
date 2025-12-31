-- Fix RagMode enum case mismatch
-- Issue: Java enum uses uppercase (PGVECTOR, NEO4J, HYBRID) but CHECK constraint was lowercase

-- 1. Drop old CHECK constraint first (to allow UPDATE)
ALTER TABLE dm_project
DROP CONSTRAINT IF EXISTS dm_project_rag_mode_check;

-- 2. Update existing data from lowercase to uppercase (if any)
UPDATE dm_project
SET rag_mode = UPPER(rag_mode)
WHERE rag_mode IS NOT NULL;

-- 3. Add new CHECK constraint with uppercase values
ALTER TABLE dm_project
ADD CONSTRAINT dm_project_rag_mode_check
    CHECK (rag_mode IN ('PGVECTOR', 'NEO4J', 'HYBRID'));
