package com.gadgetmart.catalog.service;

import com.gadgetmart.catalog.domain.Listing;
import com.gadgetmart.catalog.domain.ListingStatus;
import com.gadgetmart.catalog.dto.NamedRef;
import com.gadgetmart.catalog.dto.ProductDetail;
import com.gadgetmart.catalog.dto.ProductSnapshot;
import com.gadgetmart.catalog.dto.ProductSummary;

import java.util.ArrayList;

final class ProductMapper {

    private ProductMapper() {
    }

    static ProductSummary toSummary(Listing l, int availableQty) {
        return new ProductSummary(
                l.getId(), l.getTitle(),
                new NamedRef(l.getBrand().getSlug(), l.getBrand().getName()),
                new NamedRef(l.getCategory().getSlug(), l.getCategory().getName()),
                l.getModelName(), l.getConditionGrade(), l.getConditionGrade().label(),
                l.getPriceMinor(), l.getMrpMinor(), discountPercent(l), l.getCurrency(),
                l.getThumbnailUrl(), l.getCity(), l.getWarrantyMonths(), l.getBatteryHealthPct(),
                availableQty > 0, availableQty, l.getSellerName());
    }

    /** Must be called inside a transaction: reads the lazy image collection. */
    static ProductDetail toDetail(Listing l, int availableQty) {
        return new ProductDetail(
                l.getId(), l.getTitle(), l.getDescription(),
                new NamedRef(l.getBrand().getSlug(), l.getBrand().getName()),
                new NamedRef(l.getCategory().getSlug(), l.getCategory().getName()),
                l.getModelName(), l.getConditionGrade(), l.getConditionGrade().label(),
                l.getPriceMinor(), l.getMrpMinor(), discountPercent(l), l.getCurrency(),
                l.getBatteryHealthPct(), l.getWarrantyMonths(), l.getAttributes(),
                new ArrayList<>(l.getImageUrls()), l.getCity(), l.getSellerName(),
                availableQty > 0, availableQty, l.getCreatedAt());
    }

    static ProductSnapshot toSnapshot(Listing l, int availableQty) {
        return new ProductSnapshot(
                l.getId(), l.getTitle(), l.getPriceMinor(), l.getCurrency(),
                l.getStatus() == ListingStatus.ACTIVE, availableQty, l.getThumbnailUrl(), l.getSellerName());
    }

    private static int discountPercent(Listing l) {
        Long mrp = l.getMrpMinor();
        if (mrp == null || mrp <= l.getPriceMinor()) {
            return 0;
        }
        return (int) ((mrp - l.getPriceMinor()) * 100 / mrp);
    }
}
