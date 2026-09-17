-- Trigram index for the description filter.
--
-- The filter is LOWER(description) LIKE '%term%'. A leading wildcard rules out
-- every btree index, so the query was a sequential scan of the user's expenses.
-- pg_trgm indexes substrings and is what makes a contains-search indexable.
--
-- Kept apart from V4 on purpose: CREATE EXTENSION needs a privilege that some
-- managed Postgres hosts withhold. It works on Neon and on the official Docker
-- image. If a host refuses it, drop this one file — nothing else depends on it
-- and the query stays correct, just unindexed.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_expenses_description_trgm
    ON expenses USING gin (lower(description) gin_trgm_ops);
