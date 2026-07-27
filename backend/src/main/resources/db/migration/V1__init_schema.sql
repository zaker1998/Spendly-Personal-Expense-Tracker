-- V1: core schema for Spendly expense tracker

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(32)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE categories (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    color       VARCHAR(16),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_categories_user_name UNIQUE (user_id, name)
);

CREATE TABLE expenses (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id  BIGINT         NOT NULL REFERENCES categories(id),
    amount       NUMERIC(19, 2) NOT NULL,
    currency     VARCHAR(3)     NOT NULL DEFAULT 'EUR',
    spent_on     DATE           NOT NULL,
    description  VARCHAR(500),
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_expenses_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_expenses_user_spent_on ON expenses (user_id, spent_on);
CREATE INDEX idx_expenses_user_category ON expenses (user_id, category_id);
CREATE INDEX idx_categories_user_id ON categories (user_id);
