-- monthly budgets per category (null category_id = overall monthly budget)

CREATE TABLE budgets (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id  BIGINT         REFERENCES categories(id) ON DELETE CASCADE,
    amount       NUMERIC(19, 2) NOT NULL,
    year         INT            NOT NULL,
    month        INT            NOT NULL,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_budgets_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_budgets_month CHECK (month BETWEEN 1 AND 12)
);

CREATE UNIQUE INDEX uq_budgets_overall
    ON budgets (user_id, year, month)
    WHERE category_id IS NULL;

CREATE UNIQUE INDEX uq_budgets_category
    ON budgets (user_id, category_id, year, month)
    WHERE category_id IS NOT NULL;

CREATE INDEX idx_budgets_user_period ON budgets (user_id, year, month);
