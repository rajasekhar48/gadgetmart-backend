package com.gadgetmart.cart.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gadgetmart.cart.domain.CartLine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-through cache of a user's cart lines (ids + quantities only, never prices).
 * Postgres is the source of truth; Redis only makes reads fast. Every Redis failure is swallowed:
 * a cache outage degrades to "slower", never to "cart broken".
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CartCache {

    private static final String PREFIX = "cart:lines:";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    @Value("${app.cart.cache-ttl}")
    private Duration ttl;

    public Optional<List<CartLine>> get(UUID userId) {
        try {
            String json = redis.opsForValue().get(key(userId));
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(mapper.readValue(json, new TypeReference<List<CartLine>>() { }));
        } catch (Exception e) {
            log.warn("Cart cache read failed for {}: {}", userId, e.toString());
            return Optional.empty();
        }
    }

    public void put(UUID userId, List<CartLine> lines) {
        try {
            redis.opsForValue().set(key(userId), mapper.writeValueAsString(lines), ttl);
        } catch (Exception e) {
            log.warn("Cart cache write failed for {}: {}", userId, e.toString());
        }
    }

    /**
     * Evicts once the DB transaction has COMMITTED. Evicting earlier would let a concurrent reader
     * re-cache the old rows before the new ones are visible.
     */
    public void evictAfterCommit(UUID userId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict(userId);
                }
            });
        } else {
            evict(userId);
        }
    }

    private void evict(UUID userId) {
        try {
            redis.delete(key(userId));
        } catch (Exception e) {
            log.warn("Cart cache evict failed for {}: {}", userId, e.toString());
        }
    }

    private String key(UUID userId) {
        return PREFIX + userId;
    }
}
