package com.gadgetmart.cart.service;

import com.gadgetmart.cart.domain.Cart;
import com.gadgetmart.cart.domain.CartItem;
import com.gadgetmart.cart.domain.CartLine;
import com.gadgetmart.cart.exception.ApiException;
import com.gadgetmart.cart.repository.CartItemRepository;
import com.gadgetmart.cart.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything that touches Postgres or Redis for carts. Holds no HTTP calls, so database
 * transactions and row locks are never held open while waiting on another service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartStore {

    private final CartRepository carts;
    private final CartItemRepository items;
    private final CartCache cache;

    @Value("${app.cart.max-lines}")
    private int maxLines;

    @Value("${app.cart.max-quantity}")
    private int maxQuantity;

    @Value("${app.cart.abandon-after}")
    private Duration abandonAfter;

    /** Cache first, Postgres on a miss. */
    public List<CartLine> lines(UUID userId) {
        return cache.get(userId).orElseGet(() -> {
            List<CartLine> fromDb = items.findAllByUserIdOrderByAddedAtAsc(userId).stream()
                    .map(CartLine::from)
                    .toList();
            cache.put(userId, fromDb);
            return fromDb;
        });
    }

    /**
     * Adds or sets a line under the cart's row lock, so the quantity and line-count limits hold
     * even for concurrent requests. availableQty / price come from a fresh Catalog snapshot.
     *
     * @param additive true: quantity is added to what is already there ("Add to cart");
     *                 false: quantity replaces it ("change quantity")
     */
    @Transactional
    public void upsert(UUID userId, UUID listingId, int quantity, boolean additive,
                       int availableQty, long unitPriceMinor) {
        Instant now = Instant.now();
        carts.ensureExists(userId, now);
        Cart cart = carts.lockByUserId(userId).orElseThrow();

        Optional<CartItem> existing = items.findByUserIdAndListingId(userId, listingId);
        int newQty = additive ? existing.map(CartItem::getQuantity).orElse(0) + quantity : quantity;

        if (newQty > maxQuantity) {
            throw ApiException.conflict("MAX_QUANTITY", "You can buy at most " + maxQuantity + " units of one item");
        }
        if (newQty > availableQty) {
            throw ApiException.conflict("INSUFFICIENT_STOCK",
                    availableQty == 1 ? "Only 1 unit is available" : "Only " + availableQty + " units are available");
        }
        if (existing.isEmpty() && items.countByUserId(userId) >= maxLines) {
            throw ApiException.conflict("CART_FULL", "Your cart can hold at most " + maxLines + " different items");
        }

        CartItem item = existing.orElseGet(() -> {
            CartItem created = new CartItem();
            created.setUserId(userId);
            created.setListingId(listingId);
            return created;
        });
        item.setQuantity(newQty);
        item.setUnitPriceAtAddMinor(unitPriceMinor);
        items.save(item);

        cart.setUpdatedAt(now);
        cache.evictAfterCommit(userId);
    }

    @Transactional
    public void remove(UUID userId, UUID listingId) {
        items.deleteLine(userId, listingId);
        carts.touch(userId, Instant.now());
        cache.evictAfterCommit(userId);
    }

    /** Idempotent: clearing an empty or missing cart is fine. */
    @Transactional
    public void clear(UUID userId) {
        items.deleteAllForUser(userId);
        carts.touch(userId, Instant.now());
        cache.evictAfterCommit(userId);
    }

    /** Called by the nightly cleanup job. */
    @Transactional
    public int purgeAbandoned() {
        return carts.deleteStale(Instant.now().minus(abandonAfter));
    }
}
