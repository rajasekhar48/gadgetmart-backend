package com.gadgetmart.cart.service;

import com.gadgetmart.cart.client.CatalogClient;
import com.gadgetmart.cart.domain.CartLine;
import com.gadgetmart.cart.dto.CartCount;
import com.gadgetmart.cart.dto.CartItemView;
import com.gadgetmart.cart.dto.CartView;
import com.gadgetmart.cart.dto.ItemIssue;
import com.gadgetmart.cart.dto.ProductSnapshot;
import com.gadgetmart.cart.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestrates cart operations. Deliberately NOT @Transactional: it makes HTTP calls to Catalog,
 * and database transactions (inside CartStore) must stay short.
 */
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartStore store;
    private final CatalogClient catalog;

    /** The cart with live prices and stock from Catalog, plus per-line issues. */
    public CartView view(UUID userId) {
        List<CartLine> lines = store.lines(userId);
        if (lines.isEmpty()) {
            return CartView.empty();
        }
        Map<UUID, ProductSnapshot> snapshots = catalog.snapshots(lines.stream().map(CartLine::listingId).toList())
                .stream()
                .collect(Collectors.toMap(ProductSnapshot::id, Function.identity(), (a, b) -> a));

        List<CartItemView> items = lines.stream()
                .map(line -> toItemView(line, snapshots.get(line.listingId())))
                .toList();

        long subtotal = items.stream().filter(CartItemView::purchasable).mapToLong(CartItemView::lineTotalPaise).sum();
        int units = items.stream().mapToInt(CartItemView::quantity).sum();
        boolean ready = items.stream().allMatch(CartItemView::purchasable);
        String currency = snapshots.values().stream().map(ProductSnapshot::currency).findFirst().orElse("INR");
        return new CartView(items, units, items.size(), subtotal, currency, ready);
    }

    /** Cheap: no Catalog call. For the navbar badge. */
    public CartCount count(UUID userId) {
        List<CartLine> lines = store.lines(userId);
        return new CartCount(lines.stream().mapToInt(CartLine::quantity).sum(), lines.size());
    }

    public CartView add(UUID userId, UUID listingId, int quantity) {
        ProductSnapshot product = requireSellable(listingId);
        store.upsert(userId, listingId, quantity, true, product.availableQty(), product.pricePaise());
        return view(userId);
    }

    public CartView setQuantity(UUID userId, UUID listingId, int quantity) {
        ProductSnapshot product = requireSellable(listingId);
        store.upsert(userId, listingId, quantity, false, product.availableQty(), product.pricePaise());
        return view(userId);
    }

    public CartView remove(UUID userId, UUID listingId) {
        store.remove(userId, listingId);
        return view(userId);
    }

    public void clear(UUID userId) {
        store.clear(userId);
    }

    /** A listing must exist, be active and have stock before it can be added or its quantity changed. */
    private ProductSnapshot requireSellable(UUID listingId) {
        ProductSnapshot product = catalog.snapshots(List.of(listingId)).stream().findFirst()
                .filter(ProductSnapshot::active)
                .orElseThrow(() -> ApiException.notFound("NO_LONGER_AVAILABLE",
                        "This product does not exist or is no longer available"));
        if (product.availableQty() <= 0) {
            throw ApiException.conflict("OUT_OF_STOCK", "This product is out of stock");
        }
        return product;
    }

    private CartItemView toItemView(CartLine line, ProductSnapshot p) {
        if (p == null) {   // listing vanished from Catalog
            return new CartItemView(line.listingId(), "Item no longer listed", null, null,
                    line.quantity(), line.priceAtAddMinor(), 0, line.priceAtAddMinor(), 0, false,
                    List.of(ItemIssue.NO_LONGER_AVAILABLE));
        }
        List<ItemIssue> issues = new ArrayList<>();
        if (!p.active()) {
            issues.add(ItemIssue.NO_LONGER_AVAILABLE);
        } else if (p.availableQty() <= 0) {
            issues.add(ItemIssue.OUT_OF_STOCK);
        } else if (line.quantity() > p.availableQty()) {
            issues.add(ItemIssue.INSUFFICIENT_STOCK);
        }
        if (p.pricePaise() != line.priceAtAddMinor()) {
            issues.add(ItemIssue.PRICE_CHANGED);
        }
        boolean purchasable = p.active() && p.availableQty() >= line.quantity();
        return new CartItemView(p.id(), p.title(), p.thumbnailUrl(), p.sellerName(),
                line.quantity(), p.pricePaise(), p.pricePaise() * line.quantity(), line.priceAtAddMinor(),
                p.availableQty(), purchasable, issues);
    }
}
