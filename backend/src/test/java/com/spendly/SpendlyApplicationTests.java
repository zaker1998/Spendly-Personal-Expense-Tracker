package com.spendly;

import org.junit.jupiter.api.Test;

/**
 * Extends the shared base rather than declaring a second container: as its own
 * {@code @SpringBootTest} with its own {@code @Container} it started a separate
 * Postgres and built a separate application context on every run, for one
 * assertion that the context can be built at all.
 */
class SpendlyApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
    }
}
