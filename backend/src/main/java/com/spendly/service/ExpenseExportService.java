package com.spendly.service;

import com.spendly.dto.ExpenseDtos.ExpenseResponse;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ExpenseExportService {

    private static final Logger log = LoggerFactory.getLogger(ExpenseExportService.class);

    private static final String HEADER = "id,spentOn,category,amount,currency,description\n";

    /** Rows pulled per query. Small enough that peak memory doesn't track the export size. */
    private static final int CHUNK_SIZE = 500;

    /**
     * Characters that make a spreadsheet treat a cell as a formula instead of
     * text. Tab and CR are in here because Excel strips leading whitespace
     * before deciding.
     */
    private static final String FORMULA_TRIGGERS = "=+-@\t\r";

    private final ExpenseService expenseService;
    private final int maxRows;

    public ExpenseExportService(
            ExpenseService expenseService,
            @Value("${spendly.export.max-rows:50000}") int maxRows
    ) {
        this.expenseService = expenseService;
        this.maxRows = maxRows;
    }

    /**
     * Streams the filtered expenses as CSV straight to {@code out}.
     *
     * <p>Deliberately not {@code @Transactional}: the write is paced by the
     * client's download speed, and holding a database connection open for that
     * long is how a connection pool gets exhausted by a handful of slow readers.
     * Each chunk is its own short read-only transaction instead. The cost is
     * that a concurrent insert can land in a later chunk — for an export of your
     * own expenses that is a fair trade.
     *
     * <p>Chunks are read by seeking past the last id rather than by OFFSET. With
     * OFFSET the database re-reads and discards every row before the offset on
     * each chunk, so the cost of the export grew with the square of its size;
     * and asking for a {@code Page} added a {@code COUNT(*)} over the whole
     * filter to each of the up-to-100 chunks. Seeking on the primary key is one
     * index range scan per chunk, with no count at all.
     */
    public void writeCsv(
            OutputStream out,
            Long userId,
            Long categoryId,
            LocalDate from,
            LocalDate to,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String search
    ) throws IOException {
        Writer writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        writer.write(HEADER);

        long written = 0;
        long afterId = 0;

        while (written < maxRows) {
            int wanted = (int) Math.min(CHUNK_SIZE, maxRows - written);
            List<ExpenseResponse> chunk = expenseService.listAfterId(
                    userId, afterId, wanted, categoryId, from, to, minAmount, maxAmount, search);

            if (chunk.isEmpty()) {
                break;
            }

            for (ExpenseResponse expense : chunk) {
                writeRow(writer, expense);
                afterId = expense.id();
                // Belt and braces: the query is asked for at most `wanted` rows,
                // but the cap is a hard limit on what leaves the server and
                // should not depend on the query honouring its own limit.
                if (++written >= maxRows) {
                    break;
                }
            }

            // A short chunk means the filter is exhausted; asking again would
            // only cost a query that returns nothing.
            if (chunk.size() < wanted) {
                break;
            }
        }

        if (written >= maxRows) {
            log.warn("Export for user {} reached the {} row cap; the file may be truncated", userId, maxRows);
        }

        writer.flush();
    }

    private static void writeRow(Writer writer, ExpenseResponse e) throws IOException {
        // id, amount and currency are server-generated, so only the two
        // free-text columns need escaping.
        writer.write(String.valueOf(e.id()));
        writer.write(',');
        writer.write(String.valueOf(e.spentOn()));
        writer.write(',');
        writer.write(escape(e.categoryName()));
        writer.write(',');
        writer.write(String.valueOf(e.amount()));
        writer.write(',');
        writer.write(e.currency());
        writer.write(',');
        writer.write(escape(e.description()));
        writer.write('\n');
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }

        // CWE-1236. A description of =HYPERLINK("http://evil","click") is inert
        // text in the API and an executable formula the moment the file is
        // opened in Excel or Sheets. Prefixing an apostrophe is the standard
        // neutralisation: spreadsheets read it as "this cell is text" and drop
        // it, while every other CSV consumer sees one extra leading character.
        String neutralised = value;
        if (!value.isEmpty() && FORMULA_TRIGGERS.indexOf(value.charAt(0)) >= 0) {
            neutralised = "'" + value;
        }

        String escaped = neutralised.replace("\"", "\"\"");
        if (escaped.indexOf(',') >= 0
                || escaped.indexOf('"') >= 0
                || escaped.indexOf('\n') >= 0
                || escaped.indexOf('\r') >= 0) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}
