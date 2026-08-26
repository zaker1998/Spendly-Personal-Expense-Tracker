package com.spendly.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class ExpenseExportServiceTest {

    @Mock
    private ExpenseService expenseService;

    private static ExpenseResponse expense(long id, String category, String description) {
        return new ExpenseResponse(
                id, 7L, category, "#abcdef",
                new BigDecimal("12.50"), "EUR",
                LocalDate.of(2026, 3, 14), description,
                Instant.EPOCH, Instant.EPOCH);
    }

    private String export(ExpenseExportService service) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.writeCsv(out, 1L, null, null, null, null, null, null);
        return out.toString(StandardCharsets.UTF_8);
    }

    private void stubSinglePage(List<ExpenseResponse> rows) {
        when(expenseService.list(eq(1L), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(rows, PageRequest.of(0, 500), rows.size()));
    }

    @Test
    void writesHeaderAndRows() throws Exception {
        stubSinglePage(List.of(expense(1, "Food", "Lunch")));

        assertThat(export(new ExpenseExportService(expenseService, 50_000)))
                .isEqualTo("""
                        id,spentOn,category,amount,currency,description
                        1,2026-03-14,Food,12.50,EUR,Lunch
                        """);
    }

    @Test
    void quotesFieldsContainingSeparators() throws Exception {
        stubSinglePage(List.of(expense(1, "Food", "Dinner, drinks and a \"tip\"")));

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
        stubSinglePage(rows);

        String csv = export(new ExpenseExportService(expenseService, 50_000));

        assertThat(csv.lines().skip(1))
                .allMatch(line -> line.split(",", 6)[5].startsWith("'")
                        || line.split(",", 6)[5].startsWith("\"'"));
        assertThat(csv).doesNotContain(",=").doesNotContain(",@SUM");
    }

    @Test
    void leavesOrdinaryTextAlone() throws Exception {
        stubSinglePage(List.of(expense(1, "Food", "Weekly groceries")));

        assertThat(export(new ExpenseExportService(expenseService, 50_000)))
                .contains(",Weekly groceries")
                .doesNotContain("'");
    }

    @Test
    void rendersNullDescriptionAsEmptyField() throws Exception {
        stubSinglePage(List.of(expense(1, "Food", null)));

        assertThat(export(new ExpenseExportService(expenseService, 50_000)))
                .endsWith("1,2026-03-14,Food,12.50,EUR,\n");
    }

    /** Reading in chunks is only safe if the order is total; id provides that. */
    @Test
    void requestsAStableSortForEveryChunk() throws Exception {
        stubSinglePage(List.of(expense(1, "Food", "Lunch")));

        export(new ExpenseExportService(expenseService, 50_000));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(expenseService).list(eq(1L), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                pageable.capture());
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "id"));
    }

    @Test
    void keepsPagingUntilTheLastChunk() throws Exception {
        List<ExpenseResponse> first = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            first.add(expense(i, "Food", "Row " + i));
        }
        Page<ExpenseResponse> page0 = new PageImpl<>(first, PageRequest.of(0, 500), 501);
        Page<ExpenseResponse> page1 =
                new PageImpl<>(List.of(expense(500, "Food", "Last")), PageRequest.of(1, 500), 501);

        when(expenseService.list(eq(1L), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(page0, page1);

        String csv = export(new ExpenseExportService(expenseService, 50_000));

        verify(expenseService, times(2))
                .list(eq(1L), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any());
        assertThat(csv.lines()).hasSize(502); // header + 501 rows
        assertThat(csv).endsWith("500,2026-03-14,Food,12.50,EUR,Last\n");
    }

    @Test
    void stopsAtTheConfiguredRowCap() throws Exception {
        List<ExpenseResponse> rows = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            rows.add(expense(i, "Food", "Row " + i));
        }
        when(expenseService.list(eq(1L), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(rows, PageRequest.of(0, 500), 10_000));

        String csv = export(new ExpenseExportService(expenseService, 3));

        assertThat(csv.lines()).hasSize(4); // header + 3 rows
        verify(expenseService, times(1))
                .list(eq(1L), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any());
    }
}
