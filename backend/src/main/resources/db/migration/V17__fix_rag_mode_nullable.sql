-- Fix rag_mode column to be nullable
-- Issue: rag_mode has NOT NULL constraint but should be nullable
-- (null means use global default or request parameter)

ALTER TABLE dm_project
ALTER COLUMN rag_mode DROP NOT NULL;
