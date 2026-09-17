-- Indexes for the two hot paths that had none, plus optimistic locking.

-- Every authenticated request loads the user by email, and the lookup is
-- case-insensitive. The unique index on `email` cannot serve `lower(email) = ?`,
-- so the lookup was a sequential scan of `users` on every single API call.
CREATE INDEX idx_users_email_lower ON users (lower(email));

-- The admin endpoints filter and sort without a user_id, so none of the
-- existing (user_id, ...) composite indexes apply to them.
CREATE INDEX idx_expenses_spent_on ON expenses (spent_on DESC);
CREATE INDEX idx_expenses_category_id ON expenses (category_id);

-- Optimistic locking. Two clients editing the same row used to overwrite one
-- another silently; now the second write fails and the API answers 409.
ALTER TABLE expenses   ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE budgets    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE categories ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Bounds that validation now also enforces, restated where they cannot be
-- bypassed. NUMERIC(19,2) already caps the magnitude; these stop values that
-- are technically storable but meaningless.
ALTER TABLE expenses
    ADD CONSTRAINT chk_expenses_spent_on_range
    CHECK (spent_on >= DATE '2000-01-01' AND spent_on <= DATE '2100-12-31');

ALTER TABLE budgets
    ADD CONSTRAINT chk_budgets_year CHECK (year BETWEEN 2000 AND 2100);
