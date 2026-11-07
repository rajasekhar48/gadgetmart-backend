package com.gadgetmart.catalog.spec;

import com.gadgetmart.catalog.domain.Brand;
import com.gadgetmart.catalog.domain.Category;
import com.gadgetmart.catalog.domain.Listing;
import com.gadgetmart.catalog.domain.ListingStatus;
import com.gadgetmart.catalog.domain.Stock;
import com.gadgetmart.catalog.dto.ProductSearchParams;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Builds the dynamic WHERE clause for the buyer's search / filter request. */
public final class ListingSpecifications {

    private static final int MAX_SEARCH_TOKENS = 5;

    private ListingSpecifications() {
    }

    public static Specification<Listing> from(ProductSearchParams p) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            Join<Listing, Brand> brand = root.join("brand");
            Join<Listing, Category> category = root.join("category");

            where.add(cb.equal(root.get("status"), ListingStatus.ACTIVE));

            // free text: every word must match the title, model or brand
            if (StringUtils.hasText(p.q())) {
                String[] tokens = p.q().trim().toLowerCase(Locale.ROOT).split("\\s+");
                for (int i = 0; i < Math.min(tokens.length, MAX_SEARCH_TOKENS); i++) {
                    String like = "%" + escapeLike(tokens[i]) + "%";
                    where.add(cb.or(
                            cb.like(cb.lower(root.<String>get("title")), like, '\\'),
                            cb.like(cb.lower(root.<String>get("modelName")), like, '\\'),
                            cb.like(cb.lower(brand.<String>get("name")), like, '\\')));
                }
            }

            if (StringUtils.hasText(p.category())) {
                where.add(cb.equal(category.get("slug"), p.category().trim().toLowerCase(Locale.ROOT)));
            }
            if (p.brand() != null && !p.brand().isEmpty()) {
                List<String> slugs = p.brand().stream().map(s -> s.trim().toLowerCase(Locale.ROOT)).toList();
                where.add(brand.get("slug").in(slugs));
            }
            if (p.grade() != null && !p.grade().isEmpty()) {
                where.add(root.get("conditionGrade").in(p.grade()));
            }
            if (p.minPricePaise() != null) {
                where.add(cb.greaterThanOrEqualTo(root.<Long>get("priceMinor"), p.minPricePaise()));
            }
            if (p.maxPricePaise() != null) {
                where.add(cb.lessThanOrEqualTo(root.<Long>get("priceMinor"), p.maxPricePaise()));
            }
            if (StringUtils.hasText(p.city())) {
                where.add(cb.equal(cb.lower(root.<String>get("city")), p.city().trim().toLowerCase(Locale.ROOT)));
            }
            if (p.minBatteryHealth() != null) {
                where.add(cb.greaterThanOrEqualTo(root.<Integer>get("batteryHealthPct"), p.minBatteryHealth()));
            }
            if (p.minWarrantyMonths() != null) {
                where.add(cb.greaterThanOrEqualTo(root.<Integer>get("warrantyMonths"), p.minWarrantyMonths()));
            }

            // hide sold-out listings unless the buyer asks for them
            if (p.inStockOrDefault()) {
                Subquery<UUID> inStock = query.subquery(UUID.class);
                Root<Stock> stock = inStock.from(Stock.class);
                inStock.select(stock.<UUID>get("listingId"))
                        .where(cb.equal(stock.get("listingId"), root.get("id")),
                                cb.greaterThan(stock.<Integer>get("availableQty"), 0));
                where.add(cb.exists(inStock));
            }

            return cb.and(where.toArray(new Predicate[0]));
        };
    }

    /** Stops user input from acting as LIKE wildcards. */
    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
