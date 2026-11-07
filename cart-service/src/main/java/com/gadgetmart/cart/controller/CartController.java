package com.gadgetmart.cart.controller;

import com.gadgetmart.cart.dto.AddItemRequest;
import com.gadgetmart.cart.dto.CartCount;
import com.gadgetmart.cart.dto.CartView;
import com.gadgetmart.cart.dto.UpdateQuantityRequest;
import com.gadgetmart.cart.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** The logged-in buyer's own cart. The user id always comes from the verified JWT, never from the URL. */
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public CartView get(@AuthenticationPrincipal Jwt jwt) {
        return cartService.view(userId(jwt));
    }

    /** Lightweight badge counter (no Catalog call). */
    @GetMapping("/count")
    public CartCount count(@AuthenticationPrincipal Jwt jwt) {
        return cartService.count(userId(jwt));
    }

    /** Adds to the existing quantity if the item is already in the cart. */
    @PostMapping("/items")
    public CartView add(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddItemRequest request) {
        return cartService.add(userId(jwt), request.listingId(), request.quantity());
    }

    /** Sets the quantity (replaces it). */
    @PutMapping("/items/{listingId}")
    public CartView setQuantity(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID listingId,
                                @Valid @RequestBody UpdateQuantityRequest request) {
        return cartService.setQuantity(userId(jwt), listingId, request.quantity());
    }

    @DeleteMapping("/items/{listingId}")
    public CartView remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID listingId) {
        return cartService.remove(userId(jwt), listingId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@AuthenticationPrincipal Jwt jwt) {
        cartService.clear(userId(jwt));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
