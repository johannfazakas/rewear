-- Baseline migration for the users module.
-- The users module owns its own schema and Flyway history table
-- (flyway_schema_history_users). No tables yet — capabilities land in
-- later changes (auth integration). This proves the per-module pipeline.
SELECT 1;
