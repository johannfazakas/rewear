-- Baseline migration for the catalog module.
-- The catalog module owns its own schema and Flyway history table
-- (flyway_schema_history_catalog), independent of the users module's
-- version sequence. No tables yet — listings/categories/favorites land
-- in later changes. This proves the per-module pipeline.
SELECT 1;
