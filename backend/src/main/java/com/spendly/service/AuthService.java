package com.spendly.service;

import com.spendly.domain.Category;
import com.spendly.domain.Role;
import com.spendly.domain.User;
import com.spendly.dto.AuthDtos.AuthResponse;
import com.spendly.dto.AuthDtos.LoginRequest;
import com.spendly.dto.AuthDtos.RegisterRequest;
import com.spendly.exception.ConflictException;
import com.spendly.repository.CategoryRepository;
import com.spendly.repository.UserRepository;
import com.spendly.security.JwtService;
import com.spendly.security.UserPrincipal;
import java.util.List;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final List<DefaultCategory> DEFAULT_CATEGORIES = List.of(
            new DefaultCategory("Food", "#E76F51"),
            new DefaultCategory("Transport", "#2A9D8F"),
            new DefaultCategory("Rent", "#264653"),
            new DefaultCategory("Leisure", "#E9C46A"),
            new DefaultCategory("Other", "#6C757D")
    );

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email already registered");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.USER);
        userRepository.save(user);

        seedDefaultCategories(user);

        UserPrincipal principal = new UserPrincipal(user);
        String token = jwtService.generateToken(principal);
        return AuthResponse.of(token, user.getId(), user.getEmail(), user.getRole());
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email().trim().toLowerCase(), request.password())
        );
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        UserPrincipal principal = new UserPrincipal(user);
        String token = jwtService.generateToken(principal);
        return AuthResponse.of(token, user.getId(), user.getEmail(), user.getRole());
    }

    private void seedDefaultCategories(User user) {
        for (DefaultCategory def : DEFAULT_CATEGORIES) {
            Category category = new Category();
            category.setUser(user);
            category.setName(def.name());
            category.setColor(def.color());
            categoryRepository.save(category);
        }
    }

    private record DefaultCategory(String name, String color) {
    }
}
