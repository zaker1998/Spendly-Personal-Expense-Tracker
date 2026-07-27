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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    public CategoryService(CategoryRepository categoryRepository, UserRepository userRepository) {
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
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

        String newName = request.name().trim();
        if (!category.getName().equalsIgnoreCase(newName)
                && categoryRepository.existsByUserIdAndNameIgnoreCase(userId, newName)) {
            throw new ConflictException("Category already exists");
        }

        category.setName(newName);
        category.setColor(request.color());
        return toResponse(category);
    }

    @Transactional
    public void delete(Long userId, Long categoryId) {
        Category category = categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        if (categoryRepository.countExpensesByCategoryId(categoryId) > 0) {
            throw new BadRequestException("Cannot delete category that has expenses");
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
                category.getCreatedAt()
        );
    }
}
