package com.spendly.service;

import com.spendly.dto.UserDtos.UserResponse;
import com.spendly.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private final UserRepository userRepository;

    public AdminService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(u -> new UserResponse(u.getId(), u.getEmail(), u.getRole(), u.getCreatedAt()));
    }
}
