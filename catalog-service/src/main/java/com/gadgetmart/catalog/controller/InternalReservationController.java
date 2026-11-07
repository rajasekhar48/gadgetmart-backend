package com.gadgetmart.catalog.controller;

import com.gadgetmart.catalog.dto.ReservationResponse;
import com.gadgetmart.catalog.dto.ReserveRequest;
import com.gadgetmart.catalog.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Called by Order Service during checkout. Not routed by the gateway; needs X-Internal-Key. */
@RestController
@RequestMapping("/internal/reservations")
@RequiredArgsConstructor
public class InternalReservationController {

    private final ReservationService reservationService;

    /** 201 on success (idempotent per orderId). 409 + unavailableItems if any item is short. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse reserve(@Valid @RequestBody ReserveRequest request) {
        return reservationService.reserve(request);
    }

    @GetMapping("/{orderId}")
    public ReservationResponse get(@PathVariable UUID orderId) {
        return reservationService.get(orderId);
    }

    @PostMapping("/{orderId}/commit")
    public ReservationResponse commit(@PathVariable UUID orderId) {
        return reservationService.commit(orderId);
    }

    @PostMapping("/{orderId}/release")
    public ReservationResponse release(@PathVariable UUID orderId) {
        return reservationService.release(orderId);
    }
}
