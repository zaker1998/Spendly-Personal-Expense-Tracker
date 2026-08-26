package com.spendly.service;

import com.spendly.domain.Category;
import com.spendly.dto.ExpenseDtos.SuggestCategoryResponse;
import com.spendly.repository.CategoryRepository;
import com.spendly.service.ai.AiCategoryClient;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Suggests a category for an expense description.
 *
 * Strategy: ask the LLM first (if configured), validate its answer against the
 * user's actual categories, and fall back to a keyword heuristic when the AI is
 * unavailable or returns something unusable. The endpoint therefore works with
 * or without an API key.
 */
@Service
public class CategorySuggestionService {

    private static final Logger log = LoggerFactory.getLogger(CategorySuggestionService.class);

    /**
     * Insertion-ordered on purpose. {@code Map.of} has an unspecified iteration
     * order, so a description hitting two groups ("cinema tickets" matches both
     * leisure and transport) resolved differently between JVM runs.
     */
    private static final Map<String, List<String>> KEYWORD_GROUPS = orderedGroups(
            "food", List.of("grocery", "groceries", "restaurant", "lunch", "dinner", "breakfast", "coffee",
                    "pizza", "supermarket", "billa", "spar", "hofer", "lidl", "penny", "bakery", "snack"),
            // "ticket" deliberately omitted: cinema, concert and event tickets are
            // leisure far more often than transport. The specific operators below
            // carry the transport signal instead.
            "transport", List.of("uber", "bolt", "taxi", "train", "bus", "metro", "fuel", "gas",
                    "parking", "flight", "oebb", "öbb", "wiener linien", "scooter", "u-bahn"),
            "rent", List.of("rent", "miete", "apartment", "utilities", "electricity", "internet", "wifi",
                    "heating", "insurance"),
            "leisure", List.of("cinema", "movie", "netflix", "spotify", "gym", "concert", "game", "bar",
                    "club", "party", "book", "hobby", "dance")
    );

    @SafeVarargs
    private static Map<String, List<String>> orderedGroups(Object... pairs) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            @SuppressWarnings("unchecked")
            List<String> words = (List<String>) pairs[i + 1];
            map.put((String) pairs[i], words);
        }
        return Collections.unmodifiableMap(map);
    }

    private final CategoryRepository categoryRepository;
    private final AiCategoryClient aiCategoryClient;

    public CategorySuggestionService(CategoryRepository categoryRepository, AiCategoryClient aiCategoryClient) {
        this.categoryRepository = categoryRepository;
        this.aiCategoryClient = aiCategoryClient;
    }

    @Transactional(readOnly = true)
    public SuggestCategoryResponse suggest(Long userId, String description) {
        List<Category> categories = categoryRepository.findByUserIdOrderByNameAsc(userId);
        if (categories.isEmpty()) {
            return new SuggestCategoryResponse(null, null, "NONE");
        }

        List<String> names = categories.stream().map(Category::getName).toList();

        if (aiCategoryClient.isEnabled()) {
            Optional<String> answer = aiCategoryClient.pickCategory(description, names);
            Optional<Category> aiPick = answer.flatMap(a -> matchByName(categories, a));
            if (aiPick.isPresent()) {
                Category c = aiPick.get();
                return new SuggestCategoryResponse(c.getId(), c.getName(), "AI");
            }
            // An answer that matches nothing is the quiet failure mode: the call
            // succeeded, the fallback returns 200, and nothing says why.
            answer.ifPresent(a -> log.warn(
                    "LLM answered \"{}\" which is not one of the user's categories {}; using heuristic",
                    a, names));
        }

        return heuristicSuggestion(categories, description)
                .map(c -> new SuggestCategoryResponse(c.getId(), c.getName(), "HEURISTIC"))
                .orElseGet(() -> new SuggestCategoryResponse(null, null, "NONE"));
    }

    /**
     * The LLM answer is never trusted as-is: whatever it says, the result must be
     * one of the categories the user owns.
     *
     * Models add punctuation, quotes and the occasional "Category: " prefix even
     * when told to reply with a bare name, so the answer is normalised and then,
     * failing an exact hit, searched for a category name. Both paths still select
     * from the user's own list, so a hallucinated name can never be returned.
     */
    private Optional<Category> matchByName(List<Category> categories, String name) {
        String normalized = name.trim()
                .replaceAll("^[\"'`\\s]+|[\"'`.!,;:\\s]+$", "")
                .toLowerCase(Locale.ROOT);

        Optional<Category> exact = categories.stream()
                .filter(c -> c.getName().toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst();
        if (exact.isPresent()) {
            return exact;
        }

        // Longest name first, so "Food & Drink" wins over "Food" when both appear.
        return categories.stream()
                .filter(c -> normalized.contains(c.getName().toLowerCase(Locale.ROOT)))
                .max(Comparator.comparingInt(c -> c.getName().length()));
    }

    private Optional<Category> heuristicSuggestion(List<Category> categories, String description) {
        String text = description.toLowerCase(Locale.ROOT);

        // 1. Direct mention of a category name in the description wins.
        for (Category category : categories) {
            if (text.contains(category.getName().toLowerCase(Locale.ROOT))) {
                return Optional.of(category);
            }
        }

        // 2. Keyword groups mapped onto categories whose name contains the group key
        //    (covers the seeded defaults: Food, Transport, Rent, Leisure).
        //    "cinema tickets" matches leisure on "cinema" and transport on
        //    "ticket"; the longest matched keyword wins, and declaration order
        //    breaks ties so the result is at least deterministic.
        String bestGroup = null;
        int bestLength = 0;
        for (Map.Entry<String, List<String>> group : KEYWORD_GROUPS.entrySet()) {
            for (String word : group.getValue()) {
                if (text.contains(word) && word.length() > bestLength) {
                    bestGroup = group.getKey();
                    bestLength = word.length();
                }
            }
        }
        if (bestGroup != null) {
            final String key = bestGroup;
            Optional<Category> target = categories.stream()
                    .filter(c -> c.getName().toLowerCase(Locale.ROOT).contains(key))
                    .findFirst();
            if (target.isPresent()) {
                return target;
            }
        }

        // 3. Fall back to a category literally named "Other" if the user has one.
        return categories.stream()
                .filter(c -> c.getName().equalsIgnoreCase("Other"))
                .findFirst();
    }
}
