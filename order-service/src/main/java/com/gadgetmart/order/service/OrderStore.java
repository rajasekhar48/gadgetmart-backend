package com.gadgetmart.order.service;

import com.gadgetmart.order.domain.CustomerOrder;
import com.gadgetmart.order.domain.OrderItem;
import com.gadgetmart.order.domain.OrderStatus;
import com.gadgetmart.order.domain.OrderStatusHistory;
import com.gadgetmart.order.domain.OutboxEvent;
import com.gadgetmart.order.domain.Payment;
import com.gadgetmart.order.domain.PaymentStatus;
import com.gadgetmart.order.domain.ShippingAddress;
import com.gadgetmart.order.dto.CartSnapshot;
import com.gadgetmart.order.dto.ShippingAddressRequest;
import com.gadgetmart.order.repository.OrderRepository;
import com.gadgetmart.order.repository.OrderStatusHistoryRepository;
import com.gadgetmart.order.repository.OutboxEventRepository;
import com.gadgetmart.order.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * All database writes for orders and payments. Each method is one short transaction: the checkout
 * flow never holds a transaction open while it waits on Cart, Catalog or the payment provider.
 * Status changes and their outbox events are written in the same transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderStore {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";   // no 0/O/1/I
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyMMdd");

    /** The only legal status transitions. */
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
            OrderStatus.PENDING_PAYMENT, EnumSet.of(OrderStatus.PAID, OrderStatus.REJECTED, OrderStatus.CANCELLED),
            OrderStatus.PAID, EnumSet.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED),
            OrderStatus.SHIPPED, EnumSet.of(OrderStatus.DELIVERED),
            OrderStatus.DELIVERED, EnumSet.of(OrderStatus.COMPLETED));

    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final OrderStatusHistoryRepository history;
    private final OutboxEventRepository outbox;
    private final SecureRandom random = new SecureRandom();

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public Optional<CustomerOrder> findByIdempotencyKey(UUID userId, String key) {
        return orders.findByUserIdAndIdempotencyKey(userId, key);
    }

    @Transactional(readOnly = true)
    public Optional<CustomerOrder> find(UUID orderId) {
        return orders.findById(orderId);
    }

    @Transactional(readOnly = true)
    public Optional<Payment> latestPayment(UUID orderId) {
        return payments.findFirstByOrderIdOrderByCreatedAtDesc(orderId);
    }

    @Transactional(readOnly = true)
    public List<UUID> findPendingOlderThan(Instant cutoff, int limit) {
        return orders.findIdsByStatusCreatedBefore(OrderStatus.PENDING_PAYMENT, cutoff, PageRequest.of(0, limit));
    }

    // ------------------------------------------------------------------ order lifecycle

    /**
     * Creates the order from the validated cart. Title and prices are copied (snapshots).
     * A duplicate (user, idempotency key) raises DataIntegrityViolationException, which the caller
     * turns into "return the existing order".
     */
    @Transactional
    public CustomerOrder createPending(UUID userId, String userEmail, String idempotencyKey,
                                       ShippingAddressRequest a, CartSnapshot cart) {
        CustomerOrder order = new CustomerOrder();
        order.setOrderNumber(newOrderNumber());
        order.setUserId(userId);
        order.setUserEmail(userEmail);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setIdempotencyKey(idempotencyKey);
        order.setCurrency(cart.currency());
        order.setShippingAddress(new ShippingAddress(
                a.fullName().trim(), a.phone(), a.line1().trim(),
                a.line2() == null || a.line2().isBlank() ? null : a.line2().trim(),
                a.city().trim(), a.state().trim(), a.pincode()));

        long total = 0;
        for (CartSnapshot.Line line : cart.items()) {
            OrderItem item = new OrderItem();
            item.setListingId(line.listingId());
            item.setTitle(line.title());
            item.setSellerName(line.sellerName());
            item.setThumbnailUrl(line.thumbnailUrl());
            item.setUnitPriceMinor(line.unitPricePaise());
            item.setQuantity(line.quantity());
            item.setLineTotalMinor(line.unitPricePaise() * line.quantity());
            total += item.getLineTotalMinor();
            order.getItems().add(item);
        }
        order.setTotalMinor(total);

        orders.saveAndFlush(order);
        history.save(new OrderStatusHistory(order.getId(), null, OrderStatus.PENDING_PAYMENT, null));
        return order;
    }

    /** Stock was not available. */
    @Transactional
    public void reject(UUID orderId, String reason) {
        CustomerOrder order = lock(orderId);
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return;
        }
        transition(order, OrderStatus.REJECTED, reason);
    }

    /** Payment failed, timed out or was compensated. Only a not-yet-paid order can be cancelled here. */
    @Transactional
    public void cancel(UUID orderId, String reason) {
        CustomerOrder order = lock(orderId);
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            log.info("Not cancelling order {}: already {}", orderId, order.getStatus());
            return;
        }
        transition(order, OrderStatus.CANCELLED, reason);

        Map<String, Object> payload = baseEvent(order);
        payload.put("reason", reason);
        outbox.save(new OutboxEvent("ORDER", orderId, "OrderCancelled", payload));
    }

    /** Payment captured and stock committed: the order is placed. Idempotent. */
    @Transactional
    public void markPaid(UUID orderId) {
        CustomerOrder order = lock(orderId);
        if (order.getStatus() == OrderStatus.PAID) {
            return;
        }
        transition(order, OrderStatus.PAID, null);

        Map<String, Object> payload = baseEvent(order);
        payload.put("items", order.getItems().stream().map(i -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("listingId", i.getListingId().toString());
            m.put("title", i.getTitle());
            m.put("quantity", i.getQuantity());
            m.put("unitPricePaise", i.getUnitPriceMinor());
            return m;
        }).toList());
        payload.put("shipToCity", order.getShippingAddress().getCity());
        outbox.save(new OutboxEvent("ORDER", orderId, "OrderPaid", payload));
    }

    // ------------------------------------------------------------------ payments

    @Transactional
    public UUID startPayment(UUID orderId, String provider, long amountMinor, String currency) {
        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setProvider(provider);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmountMinor(amountMinor);
        payment.setCurrency(currency);
        return payments.save(payment).getId();
    }

    @Transactional
    public void paymentSucceeded(UUID paymentId, String reference) {
        Payment payment = payments.findById(paymentId).orElseThrow();
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setProviderReference(reference);
    }

    @Transactional
    public void paymentFailed(UUID paymentId, String reason) {
        Payment payment = payments.findById(paymentId).orElseThrow();
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(reason);
    }

    @Transactional
    public void paymentRefunded(UUID paymentId) {
        Payment payment = payments.findById(paymentId).orElseThrow();
        payment.setStatus(PaymentStatus.REFUNDED);
    }

    // ------------------------------------------------------------------ helpers

    private CustomerOrder lock(UUID orderId) {
        return orders.lockById(orderId).orElseThrow(() -> new IllegalStateException("Order not found: " + orderId));
    }

    private void transition(CustomerOrder order, OrderStatus to, String reason) {
        OrderStatus from = order.getStatus();
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new IllegalStateException("Illegal order transition " + from + " -> " + to);
        }
        order.setStatus(to);
        if ((to == OrderStatus.REJECTED || to == OrderStatus.CANCELLED) && reason != null) {
            order.setFailureReason(reason);
        }
        history.save(new OrderStatusHistory(order.getId(), from, to, reason));
    }

    private Map<String, Object> baseEvent(CustomerOrder order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId().toString());
        payload.put("orderNumber", order.getOrderNumber());
        payload.put("userId", order.getUserId().toString());
        payload.put("email", order.getUserEmail());
        payload.put("customerName", order.getShippingAddress().getFullName());
        payload.put("phone", order.getShippingAddress().getPhone());
        payload.put("totalPaise", order.getTotalMinor());
        payload.put("currency", order.getCurrency());
        return payload;
    }

    private String newOrderNumber() {
        StringBuilder sb = new StringBuilder("GM-").append(LocalDate.now(ZoneOffset.UTC).format(DAY)).append('-');
        for (int i = 0; i < 8; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
