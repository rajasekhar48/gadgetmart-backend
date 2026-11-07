package com.gadgetmart.order.service;

import com.gadgetmart.order.domain.CustomerOrder;
import com.gadgetmart.order.domain.OrderItem;
import com.gadgetmart.order.domain.OrderStatusHistory;
import com.gadgetmart.order.domain.Payment;
import com.gadgetmart.order.domain.ShippingAddress;
import com.gadgetmart.order.dto.AddressResponse;
import com.gadgetmart.order.dto.OrderItemResponse;
import com.gadgetmart.order.dto.OrderResponse;
import com.gadgetmart.order.dto.OrderSummary;
import com.gadgetmart.order.dto.PageResponse;
import com.gadgetmart.order.dto.PaymentResponse;
import com.gadgetmart.order.dto.StatusChangeResponse;
import com.gadgetmart.order.exception.ApiException;
import com.gadgetmart.order.repository.OrderRepository;
import com.gadgetmart.order.repository.OrderStatusHistoryRepository;
import com.gadgetmart.order.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Read side. Always scoped to the owner: another user's order is simply "not found". */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderQueryService {

    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final OrderStatusHistoryRepository history;

    public OrderResponse get(UUID userId, UUID orderId) {
        CustomerOrder order = orders.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "Order not found"));
        return toResponse(order);
    }

    public PageResponse<OrderSummary> list(UUID userId, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
        return PageResponse.from(orders.findByUserId(userId, pageable).map(this::toSummary));
    }

    private OrderSummary toSummary(CustomerOrder o) {
        List<OrderItem> items = o.getItems();      // loaded in batches (@BatchSize), not one query per order
        OrderItem first = items.isEmpty() ? null : items.get(0);
        return new OrderSummary(o.getId(), o.getOrderNumber(), o.getStatus().name(), o.getTotalMinor(), o.getCurrency(),
                items.stream().mapToInt(OrderItem::getQuantity).sum(),
                first == null ? null : first.getTitle(),
                first == null ? null : first.getThumbnailUrl(),
                o.getCreatedAt());
    }

    private OrderResponse toResponse(CustomerOrder o) {
        ShippingAddress a = o.getShippingAddress();
        PaymentResponse payment = payments.findFirstByOrderIdOrderByCreatedAtDesc(o.getId())
                .map(this::toPayment).orElse(null);
        List<StatusChangeResponse> timeline = history.findByOrderIdOrderByCreatedAtAsc(o.getId()).stream()
                .map(this::toChange).toList();
        return new OrderResponse(
                o.getId(), o.getOrderNumber(), o.getStatus().name(), o.getTotalMinor(), o.getCurrency(),
                o.getItems().stream().map(i -> new OrderItemResponse(i.getListingId(), i.getTitle(), i.getSellerName(),
                        i.getThumbnailUrl(), i.getQuantity(), i.getUnitPriceMinor(), i.getLineTotalMinor())).toList(),
                new AddressResponse(a.getFullName(), a.getPhone(), a.getLine1(), a.getLine2(),
                        a.getCity(), a.getState(), a.getPincode()),
                payment, timeline, o.getFailureReason(), o.getCreatedAt());
    }

    private PaymentResponse toPayment(Payment p) {
        return new PaymentResponse(p.getStatus().name(), p.getProvider(), p.getProviderReference(), p.getFailureReason());
    }

    private StatusChangeResponse toChange(OrderStatusHistory h) {
        return new StatusChangeResponse(h.getFromStatus() == null ? null : h.getFromStatus().name(),
                h.getToStatus().name(), h.getReason(), h.getCreatedAt());
    }
}
