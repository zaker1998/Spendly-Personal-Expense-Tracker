package com.spendly.service;

import com.spendly.domain.Category;
import com.spendly.domain.User;
import com.spendly.dto.CategoryDtos.CategoryRequest;
import com.spendly.dto.CategoryDtos.CategoryResponse;
import com.spendly.exception.BadRequestException;
import com.spendly.exception.ConflictException;
import com.spendly.exception.ResourceNotFoundException;
import com.spendly.repository.CategoryRepository;
import com.spendly.repository.UserRepository;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;

    public CategoryService(
            CategoryRepository categoryRepository,
            UserRepository userRepository,
            ApplicationEventPublisher events
    ) {
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(Long userId) {
        return categoryRepository.findByUserIdOrderByNameAsc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CategoryResponse create(Long userId, CategoryRequest request) {
        if (categoryRepository.existsByUserIdAndNameIgnoreCase(userId, request.name().trim())) {
            throw new ConflictException("Category already exists");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Category category = new Category();
        category.setUser(user);
        category.setName(request.name().trim());
        category.setColor(request.color());
        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(Long userId, Long categoryId, CategoryRequest request) {
        Category category = categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        ExpenseService.requireCurrentVersion(category.getVersion(), request.version());

        String newName = request.name().trim();
        boolean renamed = !category.getName().equals(newName);
        if (!category.getName().equalsIgnoreCase(newName)
                && categoryRepository.existsByUserIdAndNameIgnoreCase(userId, newName)) {
            throw new ConflictException("Category already exists");
        }

        category.setName(newName);
        category.setColor(request.color());
        // See ExpenseService.update: the version in the response has to be the
        // one the row now holds, not the one it held on the way in.
        categoryRepository.flush();
        if (renamed) {
            // The cached monthly summary embeds category names, and nothing else
            // invalidates it on a rename — the dashboard used to show the old
            // name until the entry expired.
            events.publishEvent(new CategoryRenamedEvent(userId));
        }
        return toResponse(category);
    }

    @Transactional
    public void delete(Long userId, Long categoryId) {
        Category category = categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        if (categoryRepository.countExpensesByCategoryId(categoryId) > 0) {
            throw new BadRequestException("Cannot delete category that has expenses");
        }
        // The FK is ON DELETE CASCADE, so without this check the user's budgets
        // for this category would silently disappear.
        if (categoryRepository.countBudgetsByCategoryId(categoryId) > 0) {
            throw new BadRequestException("Cannot delete category that has budgets");
        }
        categoryRepository.delete(category);
    }

    Category getOwnedOrThrow(Long userId, Long categoryId) {
        return categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getColor(),
                category.getVersion(),
                category.getCreatedAt()
        );
    }
}
