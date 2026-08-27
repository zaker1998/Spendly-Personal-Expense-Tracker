package com.spendly.config;

import com.spendly.domain.AppCurrency;
import com.spendly.domain.Budget;
import com.spendly.domain.Category;
import com.spendly.domain.Expense;
import com.spendly.domain.Role;
import com.spendly.domain.User;
import com.spendly.repository.BudgetRepository;
import com.spendly.repository.CategoryRepository;
import com.spendly.repository.ExpenseRepository;
import com.spendly.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the two demo accounts the README documents, plus three months of
 * plausible spending for the demo user.
 *
 * <p>Off unless {@code spendly.seed-demo-data} is set: the public demo turns it
 * on deliberately so anyone can log in without registering, but a real
 * deployment must never come up with a known-password admin.
 *
 * <p><strong>The demo user's data is rebuilt on every startup.</strong> That is
 * deliberate for a public demo: visitors add test rows, and without a reset the
 * thing a recruiter opens next month is a pile of junk around three-month-old
 * expenses. Rebuilding also keeps the window rolling, so the dashboard always has
 * a current month to show. The admin account is left alone.
 */
@Component
@ConditionalOnProperty(name = "spendly.seed-demo-data", havingValue = "true")
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private static final String DEMO_EMAIL = "demo@spendly.app";
    private static final String ADMIN_EMAIL = "admin@spendly.app";

    /** Fixed so every deploy produces the same figures and the screenshots stay true. */
    private static final long SEED = 20_260_827L;

    private static final int MONTHS = 3;

    private final UserRepository users;
    private final CategoryRepository categories;
    private final ExpenseRepository expenses;
    private final BudgetRepository budgets;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(
            UserRepository users,
            CategoryRepository categories,
            ExpenseRepository expenses,
            BudgetRepository budgets,
            PasswordEncoder passwordEncoder
    ) {
        this.users = users;
        this.categories = categories;
        this.expenses = expenses;
        this.budgets = budgets;
        this.passwordEncoder = passwordEncoder;
    }

    /** One transaction: a half-written demo dataset is worse than none. */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureUser(ADMIN_EMAIL, "Admin123!", Role.ADMIN);
        User demo = ensureUser(DEMO_EMAIL, "Demo123!", Role.USER);

        // Order matters: expenses and budgets both point at categories.
        expenses.deleteAllForUser(demo.getId());
        budgets.deleteAllForUser(demo.getId());
        categories.deleteAllForUser(demo.getId());

        Categories cats = createCategories(demo);
        Random random = new Random(SEED);

        List<Expense> rows = new ArrayList<>();
        List<Budget> limits = new ArrayList<>();
        YearMonth thisMonth = YearMonth.from(LocalDate.now());

        for (int back = MONTHS - 1; back >= 0; back--) {
            YearMonth month = thisMonth.minusMonths(back);
            rows.addAll(monthOfSpending(demo, cats, month, random));
            limits.addAll(monthlyBudgets(demo, cats, month));
        }

        expenses.saveAll(rows);
        budgets.saveAll(limits);

        log.info("Demo data rebuilt: {} expenses and {} budgets over {} months for {}",
                rows.size(), limits.size(), MONTHS, DEMO_EMAIL);
    }

    private User ensureUser(String email, String password, Role role) {
        return users.findByEmailIgnoreCase(email).orElseGet(() -> {
            User user = new User();
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setRole(role);
            return users.save(user);
        });
    }

    private Categories createCategories(User demo) {
        // Same five the app gives every new account, so the keyword fallback in
        // CategorySuggestionService keeps working on the demo too.
        Category food = category(demo, "Food", "#E76F51");
        Category transport = category(demo, "Transport", "#2A9D8F");
        Category rent = category(demo, "Rent", "#264653");
        Category leisure = category(demo, "Leisure", "#E9C46A");
        Category other = category(demo, "Other", "#6C757D");
        categories.saveAll(List.of(food, transport, rent, leisure, other));
        return new Categories(food, transport, rent, leisure, other);
    }

    // ---------------------------------------------------------------- data

    private record Categories(Category food, Category transport, Category rent, Category leisure, Category other) {
    }

    /** A thing you might buy, with the range it usually costs. */
    private record Item(String description, double min, double max) {
    }

    private static final List<Item> GROCERIES = List.of(
            new Item("Billa, Neubaugasse", 17.40, 52.30),
            new Item("Hofer – Wocheneinkauf", 24.10, 61.80),
            new Item("Spar Gourmet", 11.20, 33.50),
            new Item("Lidl Westbahnhof", 19.90, 47.60),
            new Item("Naschmarkt – Obst und Gemüse", 8.50, 23.40),
            new Item("Ströck – Brot und Gebäck", 3.80, 11.20));

    private static final List<Item> EATING_OUT = List.of(
            new Item("Mittagsmenü, Mensa Uni Wien", 6.50, 8.90),
            new Item("Käsekrainer, Bitzinger Würstelstand", 5.20, 6.80),
            new Item("Trzesniewski – Brötchen", 4.10, 9.60),
            new Item("Melange, Kaffee Alt Wien", 3.90, 5.40),
            new Item("Kaffee und Kuchen, Café Central", 10.80, 17.50),
            new Item("Ramen, Ryoma", 13.50, 21.00),
            new Item("Schnitzel, Gasthaus Pöschl", 18.90, 27.40),
            new Item("Falafelteller, Bosphorus", 7.20, 11.80),
            new Item("Lieferando – Pizza", 15.60, 28.30));

    private static final List<Item> TRANSPORT_EXTRAS = List.of(
            new Item("ÖBB Wien – Graz", 29.00, 48.90),
            new Item("ÖBB Wien – Salzburg", 38.00, 58.50),
            new Item("Bolt nach Hause", 8.90, 19.40),
            new Item("WienMobil Rad", 3.00, 9.00),
            new Item("Nachttaxi, 1070 → 1100", 13.50, 24.80),
            new Item("Flixbus Wien – Bratislava", 11.00, 17.90));

    private static final List<Item> LEISURE_EXTRAS = List.of(
            new Item("Stehplatz, Wiener Staatsoper", 13.00, 18.00),
            new Item("Votivkino", 10.50, 14.00),
            new Item("Bachata-Kurs, Tanzschule", 45.00, 65.00),
            new Item("Heuriger in Grinzing", 23.50, 41.90),
            new Item("Konzert, Arena Wien", 27.00, 46.50),
            new Item("Albertina – Eintritt", 17.00, 19.90),
            new Item("Amalienbad", 6.50, 9.20),
            new Item("Prater – Abend mit Freunden", 14.80, 31.60));

    private static final List<Item> OTHER_ITEMS = List.of(
            new Item("dm Drogeriemarkt", 11.60, 34.20),
            new Item("Bipa", 7.90, 21.50),
            new Item("Apotheke – Medikamente", 8.70, 27.90),
            new Item("H&M, Mariahilfer Straße", 24.90, 64.00),
            new Item("Thalia – Buch", 13.50, 27.80),
            new Item("Friseur", 25.00, 38.00),
            new Item("IKEA Vösendorf", 34.00, 94.50),
            new Item("Post – Paketversand", 4.80, 12.30));

    private List<Expense> monthOfSpending(User demo, Categories cats, YearMonth month, Random random) {
        List<Expense> rows = new ArrayList<>();

        // The current month is only partly over, so nothing may be dated ahead of
        // today — otherwise the dashboard shows spending that hasn't happened.
        LocalDate lastDay = month.equals(YearMonth.from(LocalDate.now()))
                ? LocalDate.now()
                : month.atEndOfMonth();
        int days = lastDay.getDayOfMonth();

        // Fixed monthly commitments, on roughly the day they'd actually be paid.
        rows.add(fixed(demo, cats.rent(), "Miete und Betriebskosten", month.atDay(Math.min(2, days)), "890.00"));
        add(rows, demo, cats.rent(), new Item("Wien Energie – Strom und Gas", 54.00, 68.00),
                month.atDay(Math.min(5, days)), days, random);
        rows.add(fixed(demo, cats.rent(), "A1 Internet und Mobil", month.atDay(Math.min(9, days)), "39.90"));
        rows.add(fixed(demo, cats.rent(), "ORF-Beitrag", month.atDay(Math.min(12, days)), "15.30"));
        rows.add(fixed(demo, cats.transport(), "Wiener Linien Monatskarte", month.atDay(1), "51.00"));
        rows.add(fixed(demo, cats.leisure(), "Spotify Premium", month.atDay(Math.min(4, days)), "11.99"));
        rows.add(fixed(demo, cats.leisure(), "Netflix", month.atDay(Math.min(7, days)), "13.99"));
        rows.add(fixed(demo, cats.leisure(), "Fitnessstudio, McFit Praterstern", month.atDay(Math.min(3, days)), "29.90"));

        // Everything else scales with how much of the month has actually elapsed.
        double share = days / 30.0;
        sprinkle(rows, demo, cats.food(), GROCERIES, scaled(9, share), month, days, random);
        sprinkle(rows, demo, cats.food(), EATING_OUT, scaled(13, share), month, days, random);
        sprinkle(rows, demo, cats.transport(), TRANSPORT_EXTRAS, scaled(3, share), month, days, random);
        sprinkle(rows, demo, cats.leisure(), LEISURE_EXTRAS, scaled(4, share), month, days, random);
        sprinkle(rows, demo, cats.other(), OTHER_ITEMS, scaled(5, share), month, days, random);

        return rows;
    }

    private static int scaled(int full, double share) {
        return Math.max(1, (int) Math.round(full * share));
    }

    private void sprinkle(
            List<Expense> rows, User demo, Category category, List<Item> pool,
            int count, YearMonth month, int lastDay, Random random
    ) {
        for (int i = 0; i < count; i++) {
            Item item = pool.get(random.nextInt(pool.size()));
            add(rows, demo, category, item, month.atDay(1 + random.nextInt(lastDay)), lastDay, random);
        }
    }

    private void add(List<Expense> rows, User demo, Category category, Item item,
                     LocalDate on, int lastDay, Random random) {
        double value = item.min() + random.nextDouble() * (item.max() - item.min());
        BigDecimal amount = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
        rows.add(expense(demo, category, amount, on.getDayOfMonth() > lastDay ? on.withDayOfMonth(lastDay) : on,
                item.description()));
    }

    private List<Budget> monthlyBudgets(User demo, Categories cats, YearMonth month) {
        // Set a little above what actually gets spent. A demo where every bar is
        // red reads as broken rather than as a feature; these land mostly in the
        // 70-95% band with the odd overrun, which is what a real month looks like.
        return List.of(
                budget(demo, null, "2000.00", month),
                budget(demo, cats.rent(), "1050.00", month),
                budget(demo, cats.food(), "450.00", month),
                budget(demo, cats.transport(), "150.00", month),
                budget(demo, cats.leisure(), "220.00", month));
    }

    // ------------------------------------------------------------- factories

    private static Category category(User user, String name, String color) {
        Category c = new Category();
        c.setUser(user);
        c.setName(name);
        c.setColor(color);
        return c;
    }

    private static Expense fixed(User user, Category category, String description, LocalDate on, String amount) {
        return expense(user, category, new BigDecimal(amount), on, description);
    }

    private static Expense expense(User user, Category category, BigDecimal amount, LocalDate on, String description) {
        Expense e = new Expense();
        e.setUser(user);
        e.setCategory(category);
        e.setAmount(amount);
        e.setCurrency(AppCurrency.CODE);
        e.setSpentOn(on);
        e.setDescription(description);
        return e;
    }

    private static Budget budget(User user, Category category, String amount, YearMonth month) {
        Budget b = new Budget();
        b.setUser(user);
        b.setCategory(category);
        b.setAmount(new BigDecimal(amount));
        b.setYear(month.getYear());
        b.setMonth(month.getMonthValue());
        return b;
    }
}
