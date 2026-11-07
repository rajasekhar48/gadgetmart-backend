package com.gadgetmart.cart.controller;

import com.gadgetmart.cart.dto.CartView;
import com.gadgetmart.cart.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Service-to-service (Order). Not routed by the gateway; needs X-Internal-Key. */
@RestController
@RequestMapping("/internal/carts")
@RequiredArgsConstructor
public class InternalCartController {

    private final CartService cartService;

    /** Freshly validated cart for checkout: current prices, stock and per-line issues. */
    @GetMapping("/{userId}")
    public CartView get(@PathVariable UUID userId) {
        return cartService.view(userId);
    }

    /** Order calls this after payment succeeds (idempotent). */
    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@PathVariable UUID userId) {
        cartService.clear(userId);
    }
}
