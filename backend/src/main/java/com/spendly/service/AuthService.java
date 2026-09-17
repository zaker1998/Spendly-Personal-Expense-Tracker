package com.spendly.service;

import com.spendly.domain.Category;
import com.spendly.domain.Role;
import com.spendly.domain.User;
import com.spendly.dto.AuthDtos.AuthResponse;
import com.spendly.dto.AuthDtos.ChangePasswordRequest;
import com.spendly.dto.AuthDtos.LoginRequest;
import com.spendly.dto.AuthDtos.RegisterRequest;
import com.spendly.exception.ConflictException;
import com.spendly.exception.ResourceNotFoundException;
import com.spendly.exception.TooManyAttemptsException;
import com.spendly.repository.CategoryRepository;
import com.spendly.repository.UserRepository;
import com.spendly.security.JwtService;
import com.spendly.security.LoginAttemptService;
import com.spendly.security.UserPrincipal;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

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
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;

    public AuthService(
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            LoginAttemptService loginAttemptService
    ) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.loginAttemptService = loginAttemptService;
    }

    @Transactional
    public Session register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email already registered");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.USER);
        userRepository.save(user);

        seedDefaultCategories(user);
        return newSession(user);
    }

    @Transactional
    public Session login(LoginRequest request, String clientIp) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        long lockedFor = loginAttemptService.lockedForSeconds(email);
        if (lockedFor > 0) {
            throw new TooManyAttemptsException(lockedFor);
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (AuthenticationException e) {
            loginAttemptService.recordFailure(email, clientIp);
            throw new BadCredentialsException("Invalid email or password");
        }

        loginAttemptService.recordSuccess(email);

        // The principal the provider already loaded. Looking the user up a
        // second time here was a second query on a path that runs on every
        // sign-in — and, before V4, a second full scan of the users table.
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        return newSession(user);
    }

    /** Exchanges a refresh token for a new access token, rotating the refresh token. */
    @Transactional
    public Session refresh(String refreshToken) {
        return refreshTokenService.rotate(refreshToken)
                .map(rotated -> toSession(rotated.user(), rotated.token()))
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired session"));
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    /**
     * Changing the password ends every other session.
     *
     * <p>Without that, "change my password because I think someone has my
     * account" would leave the intruder signed in for the life of their refresh
     * token, which is the one thing a user changing their password is trying to
     * prevent.
     */
    @Transactional
    public Session changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        int revoked = refreshTokenService.revokeAllForUser(userId);
        log.info("Password changed for user {}; {} session(s) revoked", userId, revoked);
        return newSession(user);
    }

    private Session newSession(User user) {
        return toSession(user, refreshTokenService.issue(user));
    }

    private Session toSession(User user, String refreshToken) {
        UserPrincipal principal = new UserPrincipal(user);
        AuthResponse body = AuthResponse.of(
                jwtService.generateToken(principal),
                jwtService.getExpirationMs() / 1000,
                user.getId(),
                user.getEmail(),
                user.getRole());
        return new Session(body, refreshToken);
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

    /** The response body plus the refresh token, which the controller puts in a cookie. */
    public record Session(AuthResponse body, String refreshToken) {
    }

    private record DefaultCategory(String name, String color) {
    }
}
