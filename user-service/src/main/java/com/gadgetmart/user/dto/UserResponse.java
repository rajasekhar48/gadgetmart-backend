package com.gadgetmart.user.dto;

import com.gadgetmart.user.domain.Role;
import com.gadgetmart.user.domain.User;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record UserResponse(
        UUID id,
        String email,
        String fullName,
        String phone,
        Set<String> roles,
        boolean enabled,
        Instant createdAt
) {
    public static UserResponse from(User u) {
        return new UserResponse(
                u.getId(), u.getEmail(), u.getFullName(), u.getPhone(),
                u.getRoles().stream().map(Role::name).collect(Collectors.toSet()),
                u.isEnabled(), u.getCreatedAt());
    }
}
