package com.spendly.service;

import com.spendly.domain.Category;
import com.spendly.dto.ExpenseDtos.SuggestCategoryResponse;
import com.spendly.repository.CategoryRepository;
import com.spendly.service.ai.AiCategoryClient;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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

    private static final Map<String, List<String>> KEYWORD_GROUPS = Map.of(
            "food", List.of("grocery", "groceries", "restaurant", "lunch", "dinner", "breakfast", "coffee",
                    "pizza", "supermarket", "billa", "spar", "hofer", "lidl", "penny", "bakery", "snack"),
            "transport", List.of("uber", "bolt", "taxi", "train", "bus", "metro", "ticket", "fuel", "gas",
                    "parking", "flight", "oebb", "öbb", "wiener linien", "scooter"),
            "rent", List.of("rent", "miete", "apartment", "utilities", "electricity", "internet", "wifi",
                    "heating", "insurance"),
            "leisure", List.of("cinema", "movie", "netflix", "spotify", "gym", "concert", "game", "bar",
                    "club", "party", "book", "hobby", "dance")
    );

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
            Optional<Category> aiPick = aiCategoryClient.pickCategory(description, names)
                    .flatMap(answer -> matchByName(categories, answer));
            if (aiPick.isPresent()) {
                Category c = aiPick.get();
                return new SuggestCategoryResponse(c.getId(), c.getName(), "AI");
            }
        }

        return heuristicSuggestion(categories, description)
                .map(c -> new SuggestCategoryResponse(c.getId(), c.getName(), "HEURISTIC"))
                .orElseGet(() -> new SuggestCategoryResponse(null, null, "NONE"));
    }

    /**
     * The LLM answer is never trusted as-is: it must exactly match (case-
     * insensitively) one of the categories the user owns.
     */
    private Optional<Category> matchByName(List<Category> categories, String name) {
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        return categories.stream()
                .filter(c -> c.getName().toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst();
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
        for (Map.Entry<String, List<String>> group : KEYWORD_GROUPS.entrySet()) {
            boolean hit = group.getValue().stream().anyMatch(text::contains);
            if (!hit) {
                continue;
            }
            Optional<Category> target = categories.stream()
                    .filter(c -> c.getName().toLowerCase(Locale.ROOT).contains(group.getKey()))
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
