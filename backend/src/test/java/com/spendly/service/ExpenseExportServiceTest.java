package com.spendly.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendly.dto.ExpenseDtos.ExpenseResponse;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpenseExportServiceTest {

    @Mock
    private ExpenseService expenseService;

    private static ExpenseResponse expense(long id, String category, String description) {
        return new ExpenseResponse(
                id, 7L, category, "#abcdef",
                new BigDecimal("12.50"), "EUR",
                LocalDate.of(2026, 3, 14), description, 0L,
                Instant.EPOCH, Instant.EPOCH);
    }

    private String export(ExpenseExportService service) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.writeCsv(out, 1L, null, null, null, null, null, null);
        return out.toString(StandardCharsets.UTF_8);
    }

    private void stubSingleChunk(List<ExpenseResponse> rows) {
        when(expenseService.listAfterId(eq(1L), anyLong(), anyInt(),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(rows);
    }

    @Test
    void writesHeaderAndRows() throws Exception {
        stubSingleChunk(List.of(expense(1, "Food", "Lunch")));

        assertThat(export(new ExpenseExportService(expenseService, 50_000)))
                .isEqualTo("""
                        id,spentOn,category,amount,currency,description
                        1,2026-03-14,Food,12.50,EUR,Lunch
                        """);
    }

    @Test
    void quotesFieldsContainingSeparators() throws Exception {
        stubSingleChunk(List.of(expense(1, "Food", "Dinner, drinks and a \"tip\"")));

        assertThat(export(new ExpenseExportService(expenseService, 50_000)))
                .contains("\"Dinner, drinks and a \"\"tip\"\"\"");
    }

    /**
     * CWE-1236: without neutralisation these cells execute when the file is
     * opened in a spreadsheet.
     */
    @Test
    void neutralisesFormulaTriggers() throws Exception {
        List<ExpenseResponse> rows = List.of(
                expense(1, "Food", "=HYPERLINK(\"http://evil\",\"click\")"),
                expense(2, "Food", "+1234"),
                expense(3, "Food", "-1+1"),
                expense(4, "Food", "@SUM(A1)"),
                expense(5, "Food", "\tleading tab"));
        stubSingleChunk(rows);

        String csv = export(new ExpenseExportService(expenseService, 50_000));

        assertThat(csv.lines().skip(1))
                .allMatch(line -> line.split(",", 6)[5].startsWith("'")
                        || line.split(",", 6)[5].startsWith("\"'"));
        assertThat(csv).doesNotContain(",=").doesNotContain(",@SUM");
    }

    @Test
    void leavesOrdinaryTextAlone() throws Exception {
        stubSingleChunk(List.of(expense(1, "Food", "Weekly groceries")));

        assertThat(export(new ExpenseExportService(expenseService, 50_000)))
                .contains(",Weekly groceries")
                .doesNotContain("'");
    }

    @Test
    void rendersNullDescriptionAsEmptyField() throws Exception {
        stubSingleChunk(List.of(expense(1, "Food", null)));

        assertThat(export(new ExpenseExportService(expenseService, 50_000)))
                .endsWith("1,2026-03-14,Food,12.50,EUR,\n");
    }

    /**
     * Chunks are read by seeking past the last id, not by OFFSET. An OFFSET walk
     * makes the database re-scan and discard everything before the offset on
     * every chunk, and the count that came with it ran the whole filter again.
     */
    @Test
    void seeksPastTheLastIdOfThePreviousChunk() throws Exception {
        List<ExpenseResponse> first = fullChunk(0);
        when(expenseService.listAfterId(eq(1L), anyLong(), anyInt(),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(first, List.of(expense(500, "Food", "Last")));

        export(new ExpenseExportService(expenseService, 50_000));

        ArgumentCaptor<Long> afterId = ArgumentCaptor.forClass(Long.class);
        verify(expenseService, times(2)).listAfterId(eq(1L), afterId.capture(), anyInt(),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull());
        // First chunk starts from the beginning, the second resumes at the last
        // id written rather than at an offset of 500.
        assertThat(afterId.getAllValues()).containsExactly(0L, 499L);
    }

    @Test
    void keepsReadingUntilAChunkComesBackShort() throws Exception {
        when(expenseService.listAfterId(eq(1L), anyLong(), anyInt(),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(fullChunk(0), List.of(expense(500, "Food", "Last")));

        String csv = export(new ExpenseExportService(expenseService, 50_000));

        verify(expenseService, times(2)).listAfterId(eq(1L), anyLong(), anyInt(),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull());
        assertThat(csv.lines()).hasSize(502); // header + 501 rows
        assertThat(csv).endsWith("500,2026-03-14,Food,12.50,EUR,Last\n");
    }

    /** An empty chunk ends the export without a further query. */
    @Test
    void stopsOnAnEmptyChunk() throws Exception {
        stubSingleChunk(List.of());

        assertThat(export(new ExpenseExportService(expenseService, 50_000)))
                .isEqualTo("id,spentOn,category,amount,currency,description\n");
    }

    @Test
    void stopsAtTheConfiguredRowCap() throws Exception {
        // Deliberately more rows than the cap: the cap must hold even if the
        // query ignores the limit it was given.
        stubSingleChunk(fullChunk(0));

        String csv = export(new ExpenseExportService(expenseService, 3));

        assertThat(csv.lines()).hasSize(4); // header + 3 rows
        verify(expenseService, times(1)).listAfterId(eq(1L), anyLong(), anyInt(),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull());
    }

    private static List<ExpenseResponse> fullChunk(int firstId) {
        List<ExpenseResponse> rows = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            rows.add(expense(firstId + i, "Food", "Row " + i));
        }
        return rows;
    }
}
