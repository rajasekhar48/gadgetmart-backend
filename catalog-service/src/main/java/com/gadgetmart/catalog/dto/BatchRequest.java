package com.gadgetmart.catalog.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record BatchRequest(@NotEmpty @Size(max = 100) List<UUID> ids) {
}
