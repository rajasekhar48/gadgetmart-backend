package com.gadgetmart.user.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,        // access token lifetime in seconds
        UserResponse user
) {
}
