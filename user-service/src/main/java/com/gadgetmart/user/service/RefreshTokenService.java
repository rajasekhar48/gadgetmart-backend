package com.gadgetmart.user.service;

import com.gadgetmart.user.config.JwtProperties;
import com.gadgetmart.user.domain.RefreshToken;
import com.gadgetmart.user.exception.ApiException;
import com.gadgetmart.user.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Opaque, rotating refresh tokens.
 *  - only the SHA-256 hash is stored (a DB leak does not leak usable tokens)
 *  - every refresh revokes the old token and issues a new one (rotation)
 *  - presenting an already-revoked token is treated as theft: all the user's tokens are revoked
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final JwtProperties props;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public String issue(UUID userId) {
        byte[] bytes = new byte[48];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTokenHash(hash(raw));
        token.setExpiresAt(Instant.now().plus(props.refreshTokenTtl()));
        repository.save(token);
        return raw;
    }

    /**
     * Validates and revokes the presented token, returning the owning user id.
     * noRollbackFor: the "revoke everything" side effect on reuse must be committed even though we throw.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public UUID consume(String raw) {
        RefreshToken token = repository.findByTokenHash(hash(raw))
                .orElseThrow(() -> ApiException.unauthorized("Invalid refresh token"));

        if (token.isRevoked()) {
            log.warn("Refresh token reuse detected for user {}: revoking all sessions", token.getUserId());
            repository.revokeAllByUserId(token.getUserId());
            throw ApiException.unauthorized("Invalid refresh token");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.unauthorized("Refresh token expired");
        }
        token.setRevoked(true);
        return token.getUserId();
    }

    @Transactional
    public void revoke(String raw) {
        repository.findByTokenHash(hash(raw)).ifPresent(t -> t.setRevoked(true));
    }

    @Transactional
    public void revokeAll(UUID userId) {
        repository.revokeAllByUserId(userId);
    }

    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
