package com.gadgetmart.catalog.dto;

import com.gadgetmart.catalog.domain.ConditionGrade;

import java.util.UUID;

/** Card shown in search / browse results. */
public record ProductSummary(
        UUID id,
        String title,
        NamedRef brand,
        NamedRef category,
        String modelName,
        ConditionGrade conditionGrade,
        String conditionLabel,
        long pricePaise,
        Long mrpPaise,
        int discountPercent,
        String currency,
        String thumbnailUrl,
        String city,
        int warrantyMonths,
        Integer batteryHealthPct,
        boolean inStock,
        int availableQty,
        String sellerName
) {
}
