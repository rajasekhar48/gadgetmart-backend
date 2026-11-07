package com.gadgetmart.order.controller;

import com.gadgetmart.order.dto.CheckoutRequest;
import com.gadgetmart.order.dto.CheckoutResult;
import com.gadgetmart.order.dto.OrderResponse;
import com.gadgetmart.order.dto.OrderSummary;
import com.gadgetmart.order.dto.PageResponse;
import com.gadgetmart.order.service.CheckoutService;
import com.gadgetmart.order.service.OrderQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** The logged-in buyer's orders. The user id always comes from the verified JWT. */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final CheckoutService checkoutService;
    private final OrderQueryService queries;

    /**
     * "Order now". Turns the caller's cart into a paid order.
     * 201 = new order placed, 200 = replay of an earlier request with the same Idempotency-Key.
     * Errors (Problem Details with a `code`): 409 EMPTY_CART / CART_NOT_READY / STOCK_UNAVAILABLE /
     * RESERVATION_EXPIRED / CHECKOUT_IN_PROGRESS, 402 PAYMENT_FAILED, 503 SERVICE_UNAVAILABLE / ORDER_CONFIRMING.
     */
    @PostMapping("/checkout")
    public ResponseEntity<OrderResponse> checkout(@AuthenticationPrincipal Jwt jwt,
                                                  @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                  @Valid @RequestBody CheckoutRequest request) {
        CheckoutResult result = checkoutService.checkout(
                UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("email"), idempotencyKey, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.order());
    }

    @GetMapping
    public PageResponse<OrderSummary> list(@AuthenticationPrincipal Jwt jwt,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        return queries.list(UUID.fromString(jwt.getSubject()), page, size);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return queries.get(UUID.fromString(jwt.getSubject()), id);
    }
}
