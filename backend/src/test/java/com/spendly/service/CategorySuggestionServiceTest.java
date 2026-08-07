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
