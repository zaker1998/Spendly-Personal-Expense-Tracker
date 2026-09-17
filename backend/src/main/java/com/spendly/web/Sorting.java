package com.spendly.web;

import com.spendly.exception.BadRequestException;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Checks the {@code sort} query parameter against the fields an endpoint is
 * willing to order by.
 *
 * <p>The repositories carry explicit JPQL, so Spring Data appends whatever the
 * client asked for without resolving it; Hibernate then fails while parsing the
 * query, and the failure arrives as a generic data-access exception, which the
 * catch-all reported as 500 — with a stack trace in the log for what is only a
 * typo in a query string.
 *
 * <p>Checking up front turns that into a 400 that names the field and lists the
 * alternatives, and has the side effect of keeping the sortable surface to
 * fields the API actually means to expose.
 */
final class Sorting {

    private Sorting() {
    }

    static Pageable validate(Pageable pageable, Set<String> allowed) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowed.contains(order.getProperty())) {
                throw new BadRequestException("Cannot sort by '" + order.getProperty()
                        + "'. Allowed fields: " + String.join(", ", new TreeSet<>(allowed)));
            }
        }
        return pageable;
    }
}
