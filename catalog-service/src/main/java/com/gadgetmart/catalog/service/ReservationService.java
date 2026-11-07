package com.gadgetmart.catalog.service;

import com.gadgetmart.catalog.domain.Reservation;
import com.gadgetmart.catalog.domain.ReservationItem;
import com.gadgetmart.catalog.domain.ReservationStatus;
import com.gadgetmart.catalog.domain.Stock;
import com.gadgetmart.catalog.dto.ReservationResponse;
import com.gadgetmart.catalog.dto.ReserveRequest;
import com.gadgetmart.catalog.dto.UnavailableItem;
import com.gadgetmart.catalog.exception.ApiException;
import com.gadgetmart.catalog.exception.InsufficientStockException;
import com.gadgetmart.catalog.repository.ReservationRepository;
import com.gadgetmart.catalog.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Stock reservation for checkout:  reserve (hold) -> commit (paid) | release (cancelled) | expire (timeout).
 * Every operation is idempotent so Order Service can safely retry.
 */
@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final int EXPIRY_BATCH_SIZE = 100;

    private final ReservationRepository reservations;
    private final StockRepository stock;

    @Value("${app.reservation.ttl}")
    private Duration ttl;

    /** All-or-nothing: if any item is short, the whole transaction rolls back and nothing is held. */
    @Transactional
    public ReservationResponse reserve(ReserveRequest request) {
        Optional<Reservation> existing = reservations.findByOrderId(request.orderId());
        if (existing.isPresent()) {
            return toResponse(existing.get());          // retry of the same order: return the original hold
        }

        // merge duplicate lines and iterate in a fixed order (sorted by id) to avoid deadlocks
        Map<UUID, Integer> wanted = new TreeMap<>();
        request.items().forEach(i -> wanted.merge(i.listingId(), i.quantity(), Integer::sum));

        Instant now = Instant.now();
        List<UnavailableItem> unavailable = new ArrayList<>();
        wanted.forEach((listingId, qty) -> {
            if (stock.reserve(listingId, qty, now) == 0) {
                int available = stock.findById(listingId).map(Stock::getAvailableQty).orElse(0);
                unavailable.add(new UnavailableItem(listingId, qty, available));
            }
        });
        if (!unavailable.isEmpty()) {
            throw new InsufficientStockException(unavailable);      // rolls back the stock updates above
        }

        Reservation reservation = new Reservation();
        reservation.setOrderId(request.orderId());
        reservation.setStatus(ReservationStatus.HELD);
        reservation.setExpiresAt(now.plus(ttl));
        wanted.forEach((listingId, qty) -> reservation.getItems().add(new ReservationItem(listingId, qty)));
        return toResponse(reservations.saveAndFlush(reservation));
    }

    /**
     * Payment succeeded: the held units become sold. A HELD reservation that is slightly past its
     * expiry is still committed (the expiry job has not claimed it yet), so a payment that lands
     * a moment late does not need a refund.
     */
    @Transactional
    public ReservationResponse commit(UUID orderId) {
        Reservation r = lock(orderId);
        if (r.getStatus() == ReservationStatus.COMMITTED) {
            return toResponse(r);
        }
        if (r.getStatus() != ReservationStatus.HELD) {
            throw ApiException.conflict("Reservation is " + r.getStatus() + " and can no longer be committed");
        }
        Instant now = Instant.now();
        for (ReservationItem item : r.getItems()) {
            if (stock.commit(item.getListingId(), item.getQuantity(), now) == 0) {
                throw new IllegalStateException("Stock invariant violated for listing " + item.getListingId());
            }
        }
        r.setStatus(ReservationStatus.COMMITTED);
        return toResponse(r);
    }

    /** Order cancelled / payment failed: units go back to available. */
    @Transactional
    public ReservationResponse release(UUID orderId) {
        Reservation r = lock(orderId);
        if (r.getStatus() == ReservationStatus.RELEASED || r.getStatus() == ReservationStatus.EXPIRED) {
            return toResponse(r);
        }
        if (r.getStatus() == ReservationStatus.COMMITTED) {
            throw ApiException.conflict("Reservation is already committed (sold); use the return flow instead");
        }
        giveBack(r, ReservationStatus.RELEASED);
        return toResponse(r);
    }

    @Transactional(readOnly = true)
    public ReservationResponse get(UUID orderId) {
        return reservations.findByOrderId(orderId)
                .map(this::toResponse)
                .orElseThrow(() -> ApiException.notFound("Reservation not found"));
    }

    /** Called by the scheduled job. SKIP LOCKED lets several replicas share the work. */
    @Transactional
    public int expireDue() {
        List<Reservation> due = reservations.findExpired(
                ReservationStatus.HELD, Instant.now(), PageRequest.of(0, EXPIRY_BATCH_SIZE));
        // TODO (Kafka phase): publish ReservationExpired via the transactional outbox for Order / Notification
        due.forEach(r -> giveBack(r, ReservationStatus.EXPIRED));
        return due.size();
    }

    private void giveBack(Reservation r, ReservationStatus newStatus) {
        Instant now = Instant.now();
        for (ReservationItem item : r.getItems()) {
            if (stock.release(item.getListingId(), item.getQuantity(), now) == 0) {
                throw new IllegalStateException("Stock invariant violated for listing " + item.getListingId());
            }
        }
        r.setStatus(newStatus);
    }

    private Reservation lock(UUID orderId) {
        return reservations.lockByOrderId(orderId)
                .orElseThrow(() -> ApiException.notFound("Reservation not found"));
    }

    private ReservationResponse toResponse(Reservation r) {
        return new ReservationResponse(
                r.getOrderId(), r.getStatus(), r.getExpiresAt(),
                r.getItems().stream().map(i -> new ReservationResponse.Item(i.getListingId(), i.getQuantity())).toList());
    }
}
