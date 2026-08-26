package com.spendly.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.spendly.domain.Category;
import com.spendly.dto.ExpenseDtos.SuggestCategoryResponse;
import com.spendly.repository.CategoryRepository;
import com.spendly.service.ai.AiCategoryClient;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategorySuggestionServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private AiCategoryClient aiCategoryClient;

    @InjectMocks
    private CategorySuggestionService service;

    @Test
    void acceptsAnLlmAnswerWithTrailingPunctuation() {
        when(categoryRepository.findByUserIdOrderByNameAsc(anyLong())).thenReturn(defaultCategories());
        when(aiCategoryClient.isEnabled()).thenReturn(true);
        when(aiCategoryClient.pickCategory(anyString(), anyList())).thenReturn(Optional.of("Food."));

        SuggestCategoryResponse response = service.suggest(1L, "lunch somewhere");

        assertThat(response.source()).isEqualTo("AI");
        assertThat(response.categoryName()).isEqualTo("Food");
    }

    @Test
    void acceptsAnLlmAnswerWrappedInQuotesOrPrefixed() {
        when(categoryRepository.findByUserIdOrderByNameAsc(anyLong())).thenReturn(defaultCategories());
        when(aiCategoryClient.isEnabled()).thenReturn(true);
        when(aiCategoryClient.pickCategory(anyString(), anyList()))
                .thenReturn(Optional.of("Category: \"Transport\""));

        SuggestCategoryResponse response = service.suggest(1L, "train ticket");

        assertThat(response.source()).isEqualTo("AI");
        assertThat(response.categoryName()).isEqualTo("Transport");
    }

    @Test
    void neverReturnsACategoryTheUserDoesNotOwn() {
        when(categoryRepository.findByUserIdOrderByNameAsc(anyLong())).thenReturn(defaultCategories());
        when(aiCategoryClient.isEnabled()).thenReturn(true);
        when(aiCategoryClient.pickCategory(anyString(), anyList()))
                .thenReturn(Optional.of("Cryptocurrency"));

        SuggestCategoryResponse response = service.suggest(1L, "bought some bitcoin");

        assertThat(response.source()).isNotEqualTo("AI");
        assertThat(response.categoryName()).isNotEqualTo("Cryptocurrency");
    }

    @Test
    void resolvesAmbiguousDescriptionsToTheMoreSpecificGroup() {
        when(categoryRepository.findByUserIdOrderByNameAsc(anyLong())).thenReturn(defaultCategories());
        when(aiCategoryClient.isEnabled()).thenReturn(false);

        assertThat(service.suggest(1L, "cinema tickets").categoryName()).isEqualTo("Leisure");
        assertThat(service.suggest(1L, "Wiener Linien annual ticket").categoryName()).isEqualTo("Transport");
        assertThat(service.suggest(1L, "Billa groceries").categoryName()).isEqualTo("Food");
    }

    private List<Category> defaultCategories() {
        return List.of(
                category(1L, "Food"),
                category(2L, "Leisure"),
                category(3L, "Other"),
                category(4L, "Rent"),
                category(5L, "Transport")
        );
    }

    private Category category(Long id, String name) {
        Category c = new Category();
        c.setId(id);
        c.setName(name);
        return c;
    }

    @Test
    void usesAiAnswerWhenItMatchesAnOwnedCategory() {
        when(categoryRepository.findByUserIdOrderByNameAsc(1L)).thenReturn(defaultCategories());
        when(aiCategoryClient.isEnabled()).thenReturn(true);
        when(aiCategoryClient.pickCategory(anyString(), anyList())).thenReturn(Optional.of("transport"));

        SuggestCategoryResponse response = service.suggest(1L, "Monthly U-Bahn pass");

        assertThat(response.categoryId()).isEqualTo(5L);
        assertThat(response.categoryName()).isEqualTo("Transport");
        assertThat(response.source()).isEqualTo("AI");
    }

    @Test
    void rejectsHallucinatedAiAnswerAndFallsBackToHeuristic() {
        when(categoryRepository.findByUserIdOrderByNameAsc(1L)).thenReturn(defaultCategories());
        when(aiCategoryClient.isEnabled()).thenReturn(true);
        when(aiCategoryClient.pickCategory(anyString(), anyList())).thenReturn(Optional.of("Groceries & Fun"));

        SuggestCategoryResponse response = service.suggest(1L, "Lunch at the pizza place");

        assertThat(response.categoryName()).isEqualTo("Food");
        assertThat(response.source()).isEqualTo("HEURISTIC");
    }

    @Test
    void heuristicMatchesKeywordsWhenAiDisabled() {
        when(categoryRepository.findByUserIdOrderByNameAsc(1L)).thenReturn(defaultCategories());
        when(aiCategoryClient.isEnabled()).thenReturn(false);

        SuggestCategoryResponse response = service.suggest(1L, "Uber to the airport");

        assertThat(response.categoryId()).isEqualTo(5L);
        assertThat(response.categoryName()).isEqualTo("Transport");
        assertThat(response.source()).isEqualTo("HEURISTIC");
    }

    @Test
    void directCategoryNameMentionWinsOverKeywords() {
        when(categoryRepository.findByUserIdOrderByNameAsc(1L)).thenReturn(defaultCategories());
        when(aiCategoryClient.isEnabled()).thenReturn(false);

        SuggestCategoryResponse response = service.suggest(1L, "rent for August");

        assertThat(response.categoryName()).isEqualTo("Rent");
    }

    @Test
    void fallsBackToOtherWhenNothingMatches() {
        when(categoryRepository.findByUserIdOrderByNameAsc(1L)).thenReturn(defaultCategories());
        when(aiCategoryClient.isEnabled()).thenReturn(false);

        SuggestCategoryResponse response = service.suggest(1L, "xyz123");

        assertThat(response.categoryName()).isEqualTo("Other");
        assertThat(response.source()).isEqualTo("HEURISTIC");
    }

    @Test
    void returnsNoneWhenUserHasNoCategories() {
        when(categoryRepository.findByUserIdOrderByNameAsc(anyLong())).thenReturn(List.of());

        SuggestCategoryResponse response = service.suggest(1L, "anything");

        assertThat(response.categoryId()).isNull();
        assertThat(response.source()).isEqualTo("NONE");
    }
}
