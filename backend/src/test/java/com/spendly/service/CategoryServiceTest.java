package com.spendly.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendly.domain.Category;
import com.spendly.domain.Role;
import com.spendly.domain.User;
import com.spendly.dto.CategoryDtos.CategoryRequest;
import com.spendly.exception.ConflictException;
import com.spendly.repository.CategoryRepository;
import com.spendly.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void createPersistsCategoryForUser() {
        User user = new User();
        user.setId(1L);
        user.setEmail("demo@spendly.app");
        user.setRole(Role.USER);

        when(categoryRepository.existsByUserIdAndNameIgnoreCase(1L, "Groceries")).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> {
            Category c = inv.getArgument(0);
            c.setId(10L);
            return c;
        });

        var response = categoryService.create(1L, new CategoryRequest("Groceries", "#111111"));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("Groceries");

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(user);
    }

    @Test
    void createRejectsDuplicateName() {
        when(categoryRepository.existsByUserIdAndNameIgnoreCase(1L, "Food")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.create(1L, new CategoryRequest("Food", null)))
                .isInstanceOf(ConflictException.class);
    }
}
