-- The API used to accept any 3-letter currency code per expense while every
-- aggregate (monthly summary, budget spend) summed the amounts and labelled the
-- result EUR. Mixed-currency rows therefore produced a wrong total.
--
-- Spendly is single-currency until FX rates are modelled, so normalise whatever
-- got in and enforce it at the schema level.

UPDATE expenses SET currency = 'EUR' WHERE currency <> 'EUR';

ALTER TABLE expenses
    ADD CONSTRAINT chk_expenses_currency CHECK (currency = 'EUR');
