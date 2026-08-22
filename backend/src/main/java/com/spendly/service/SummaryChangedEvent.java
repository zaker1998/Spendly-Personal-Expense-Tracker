package com.spendly.service;

import java.time.LocalDate;

/**
 * Raised by an expense write that changes the totals of the month {@code spentOn}
 * falls in. Consumed after the transaction commits, so a write that rolls back
 * never evicts anything.
 */
public record SummaryChangedEvent(Long userId, LocalDate spentOn) {
}
