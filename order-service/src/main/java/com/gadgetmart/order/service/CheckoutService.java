package com.gadgetmart.order.service;

import com.gadgetmart.order.client.CartClient;
import com.gadgetmart.order.client.CatalogClient;
import com.gadgetmart.order.domain.CustomerOrder;
import com.gadgetmart.order.domain.OrderStatus;
import com.gadgetmart.order.domain.Payment;
import com.gadgetmart.order.domain.PaymentStatus;
import com.gadgetmart.order.dto.CartSnapshot;
import com.gadgetmart.order.dto.CheckoutRequest;
import com.gadgetmart.order.dto.CheckoutResult;
import com.gadgetmart.order.dto.ReserveItem;
import com.gadgetmart.order.exception.ApiException;
import com.gadgetmart.order.exception.ReservationLostException;
import com.gadgetmart.order.exception.StockUnavailableException;
import com.gadgetmart.order.payment.PaymentProvider;
import com.gadgetmart.order.payment.PaymentRequest;
import com.gadgetmart.order.payment.PaymentResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * "Order now": cart -> order -> reserve stock -> pay -> commit stock -> clear cart.
 *
 * Deliberately NOT @Transactional. It talks to other services and the payment provider, so every
 * database step goes through OrderStore as its own short transaction. When a step fails, the steps
 * before it are compensated (release stock / refund), so the buyer is never charged for nothing and
 * stock is never stuck.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private static final Pattern KEY_FORMAT = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    private final OrderStore store;
    private final OrderQueryService queries;
    private final CartClient carts;
    private final CatalogClient catalog;
    private final PaymentProvider paymentProvider;

    @Value("${app.checkout.stale-after}")
    private Duration staleAfter;

    public CheckoutResult checkout(UUID userId, String userEmail, String idempotencyKey, CheckoutRequest request) {
        if (idempotencyKey == null || !KEY_FORMAT.matcher(idempotencyKey).matches()) {
            throw ApiException.badRequest("INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key header must be 8-64 characters: letters, digits, '-' or '_'");
        }

        // 1. Same key again (double click, network retry): return the original result, never a second order.
        Optional<CustomerOrder> existing = store.findByIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            return replay(userId, existing.get());
        }

        // 2. Validated cart with live prices and stock
        CartSnapshot cart = carts.getCart(userId);
        requireCheckoutReady(cart);

        // 3. Create the order (PENDING_PAYMENT) with price snapshots
        CustomerOrder order;
        try {
            order = store.createPending(userId, userEmail, idempotencyKey, request.shippingAddress(), cart);
        } catch (DataIntegrityViolationException e) {      // lost a race against the same key
            CustomerOrder winner = store.findByIdempotencyKey(userId, idempotencyKey).orElseThrow(() -> e);
            return replay(userId, winner);
        }
        UUID orderId = order.getId();

        // 4. Hold the stock (all-or-nothing)
        try {
            catalog.reserve(orderId, cart.items().stream()
                    .map(l -> new ReserveItem(l.listingId(), l.quantity())).toList());
        } catch (StockUnavailableException e) {
            store.reject(orderId, "Some items are no longer available");
            throw new ApiException(HttpStatus.CONFLICT, "STOCK_UNAVAILABLE",
                    "Some items just sold out. Please review your cart.",
                    Map.of("orderId", orderId, "unavailableItems", e.getItems()));
        } catch (RuntimeException e) {
            // A timeout does not tell us whether the hold was placed: undo it if it was, then fail the order.
            releaseQuietly(orderId);
            store.cancel(orderId, "Could not reserve stock");
            throw e;
        }

        // 5. Take the payment
        long total = order.getTotalMinor();
        String currency = order.getCurrency();
        UUID paymentId = store.startPayment(orderId, paymentProvider.name(), total, currency);
        PaymentResult result;
        try {
            result = paymentProvider.charge(new PaymentRequest(
                    orderId, userId, total, currency, "order-" + orderId, request.simulatePayment()));
        } catch (RuntimeException e) {
            log.error("Payment provider threw for order {}", orderId, e);
            result = PaymentResult.failed("Payment provider error");
        }

        if (!result.success()) {
            store.paymentFailed(paymentId, result.failureReason());
            releaseQuietly(orderId);
            store.cancel(orderId, "Payment failed: " + result.failureReason());
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "PAYMENT_FAILED",
                    result.failureReason() + ". You have not been charged.",
                    Map.of("orderId", orderId, "orderNumber", order.getOrderNumber()));
        }
        store.paymentSucceeded(paymentId, result.reference());

        // 6. Make the stock sold
        try {
            catalog.commit(orderId);
        } catch (ReservationLostException e) {
            // Our hold is gone (expired): the money must go back.
            refundQuietly(paymentId, result.reference(), total);
            store.cancel(orderId, "Stock reservation expired");
            throw new ApiException(HttpStatus.CONFLICT, "RESERVATION_EXPIRED",
                    "Your reservation expired and the payment has been refunded. Please try again.",
                    Map.of("orderId", orderId));
        } catch (RuntimeException e) {
            // Catalog unreachable AFTER payment: we cannot tell whether the commit landed. Do NOT cancel
            // (that could leak stock). The recovery job re-runs the commit, which is idempotent.
            log.warn("Commit failed for paid order {}; recovery job will finish it: {}", orderId, e.toString());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ORDER_CONFIRMING",
                    "Your payment was received and your order is being confirmed. Check My orders in a minute.",
                    Map.of("orderId", orderId, "orderNumber", order.getOrderNumber()));
        }

        // 7. Order placed
        finishPaid(userId, orderId);
        return new CheckoutResult(queries.get(userId, orderId), true);
    }

    /**
     * Safety net, called by the recovery job for PENDING_PAYMENT orders that were left behind
     * (crash, timeout after payment, ...). Safe to run repeatedly.
     */
    public void recover(UUID orderId) {
        Optional<CustomerOrder> found = store.find(orderId);
        if (found.isEmpty() || found.get().getStatus() != OrderStatus.PENDING_PAYMENT) {
            return;
        }
        CustomerOrder order = found.get();
        Optional<Payment> payment = store.latestPayment(orderId);

        if (payment.isPresent() && payment.get().getStatus() == PaymentStatus.SUCCEEDED) {
            // money was taken: finish the order, or refund if the stock hold is truly gone
            Payment p = payment.get();
            try {
                catalog.commit(orderId);
                finishPaid(order.getUserId(), orderId);
                log.info("Recovered paid order {}", orderId);
            } catch (ReservationLostException e) {
                refundQuietly(p.getId(), p.getProviderReference(), p.getAmountMinor());
                store.cancel(orderId, "Stock reservation expired");
                log.warn("Order {} could not be confirmed (stock hold lost); payment refunded", orderId);
            } catch (RuntimeException e) {
                log.warn("Recovery of order {} will be retried: {}", orderId, e.toString());
            }
        } else if (order.getCreatedAt().isBefore(Instant.now().minus(staleAfter))) {
            releaseQuietly(orderId);
            store.cancel(orderId, "Checkout timed out");
            log.info("Cancelled stale order {}", orderId);
        }
    }

    // ------------------------------------------------------------------ helpers

    private void finishPaid(UUID userId, UUID orderId) {
        store.markPaid(orderId);
        try {
            carts.clearCart(userId);
        } catch (RuntimeException e) {      // the order is placed; a leftover cart is only an inconvenience
            log.warn("Could not clear cart of user {} after order {}: {}", userId, orderId, e.toString());
        }
    }

    private CheckoutResult replay(UUID userId, CustomerOrder existing) {
        if (existing.getStatus() == OrderStatus.PENDING_PAYMENT) {
            throw ApiException.conflict("CHECKOUT_IN_PROGRESS",
                    "This order is still being processed. Please wait a moment.");
        }
        return new CheckoutResult(queries.get(userId, existing.getId()), false);
    }

    private void requireCheckoutReady(CartSnapshot cart) {
        if (cart.items() == null || cart.items().isEmpty()) {
            throw ApiException.conflict("EMPTY_CART", "Your cart is empty");
        }
        if (!cart.checkoutReady()) {
            List<Map<String, Object>> problems = cart.items().stream()
                    .filter(l -> !l.purchasable())
                    .map(l -> Map.<String, Object>of(
                            "listingId", l.listingId(),
                            "title", l.title(),
                            "issues", l.issues() == null ? List.of() : l.issues()))
                    .toList();
            throw new ApiException(HttpStatus.CONFLICT, "CART_NOT_READY",
                    "Some items in your cart can't be bought right now. Please review your cart.",
                    Map.of("items", problems));
        }
    }

    private void releaseQuietly(UUID orderId) {
        try {
            catalog.release(orderId);
        } catch (RuntimeException e) {
            log.warn("Could not release stock for order {} (the hold expires by itself): {}", orderId, e.toString());
        }
    }

    private void refundQuietly(UUID paymentId, String reference, long amountMinor) {
        try {
            paymentProvider.refund(reference, amountMinor);
            store.paymentRefunded(paymentId);
        } catch (RuntimeException e) {
            log.error("REFUND FAILED for payment {} (ref {}, amount {}): MANUAL RECONCILIATION NEEDED",
                    paymentId, reference, amountMinor, e);
        }
    }
}
