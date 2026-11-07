package com.gadgetmart.catalog.dto;

/** Slug + display name, used for categories and brands (slug is what filters use). */
public record NamedRef(String slug, String name) {
}
