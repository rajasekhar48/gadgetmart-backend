package com.gadgetmart.user.service;

import com.gadgetmart.user.domain.OutboxEvent;
import com.gadgetmart.user.domain.Role;
import com.gadgetmart.user.domain.User;
import com.gadgetmart.user.dto.AuthResponse;
import com.gadgetmart.user.dto.LoginRequest;
import com.gadgetmart.user.dto.RegisterRequest;
import com.gadgetmart.user.dto.UserResponse;
import com.gadgetmart.user.exception.ApiException;
import com.gadgetmart.user.repository.OutboxEventRepository;
import com.gadgetmart.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokens;
    private final RefreshTokenService refreshTokens;
    private final OutboxEventRepository outbox;
    /** Used to burn the same CPU time when the email is unknown, so login timing does not reveal valid emails. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder,
                       JwtTokenService jwtTokens, RefreshTokenService refreshTokens,
                       OutboxEventRepository outbox) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokens = jwtTokens;
        this.refreshTokens = refreshTokens;
        this.outbox = outbox;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = normalize(req.email());
        Role requested = req.role() == null ? Role.BUYER : req.role();
        if (requested == Role.ADMIN) {
            throw ApiException.forbidden("ADMIN accounts cannot be self-registered");
        }
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("Email already registered");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setFullName(req.fullName().trim());
        user.setPhone(req.phone());
        user.getRoles().add(Role.BUYER);              // everyone can buy
        if (requested == Role.SELLER) {
            user.getRoles().add(Role.SELLER);
        }

        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {  // two concurrent registrations, same email
            throw ApiException.conflict("Email already registered");
        }
        // Same transaction as the user row: either both are saved or neither (Notification sends the welcome message)
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("userId", user.getId().toString());
        event.put("email", user.getEmail());
        event.put("fullName", user.getFullName());
        event.put("phone", user.getPhone());
        event.put("roles", user.getRoles().stream().map(Enum::name).sorted().toList());
        outbox.save(new OutboxEvent("USER", user.getId(), "UserRegistered", event));
        return issueTokens(user);
    }

    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmail(normalize(req.email())).orElse(null);
        boolean passwordOk = passwordEncoder.matches(
                req.password(), user != null ? user.getPasswordHash() : dummyHash);

        if (user == null || !passwordOk) {
            throw ApiException.unauthorized("Invalid email or password");   // same message for both cases
        }
        if (!user.isEnabled()) {
            throw ApiException.forbidden("Account is disabled");
        }
        return issueTokens(user);
    }

    /** Deliberately NOT @Transactional: RefreshTokenService.consume must commit its revocations even on failure. */
    public AuthResponse refresh(String rawRefreshToken) {
        UUID userId = refreshTokens.consume(rawRefreshToken);
        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Invalid refresh token"));
        if (!user.isEnabled()) {
            throw ApiException.forbidden("Account is disabled");
        }
        return issueTokens(user);
    }

    public void logout(String rawRefreshToken) {
        refreshTokens.revoke(rawRefreshToken);
    }

    private AuthResponse issueTokens(User user) {
        return new AuthResponse(
                jwtTokens.createAccessToken(user),
                refreshTokens.issue(user.getId()),
                "Bearer",
                jwtTokens.accessTokenTtlSeconds(),
                UserResponse.from(user));
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
