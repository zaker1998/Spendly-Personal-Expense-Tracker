package com.spendly.exception;

/**
 * The account is temporarily locked after repeated failed logins.
 *
 * <p>Carries the remaining lock time so the response can set {@code Retry-After}
 * and the client can say something more useful than "try again later".
 */
public class TooManyAttemptsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyAttemptsException(long retryAfterSeconds) {
        super("Too many failed login attempts. Try again in " + retryAfterSeconds + "s.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
