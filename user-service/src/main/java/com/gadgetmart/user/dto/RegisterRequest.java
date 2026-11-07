package com.gadgetmart.user.dto;

import com.gadgetmart.user.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,   // 72 = BCrypt input limit
        @NotBlank @Size(max = 120) String fullName,
        @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "must be 10-15 digits") String phone,
        Role role                                              // BUYER (default) or SELLER
) {
}
