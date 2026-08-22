package com.spendly.domain;

/**
 * Spendly tracks one currency.
 *
 * Real multi-currency needs an FX rate per transaction date and a rate history
 * table — without that, summing rows of mixed currencies produces a number that
 * looks right and isn't. Until that exists the API accepts EUR only, and every
 * place that needs the code reads it from here so widening the scope later is a
 * contained change rather than a hunt for string literals.
 */
public final class AppCurrency {

    public static final String CODE = "EUR";

    private AppCurrency() {
    }
}
