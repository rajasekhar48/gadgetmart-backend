package com.gadgetmart.user.service;

import com.gadgetmart.user.domain.Role;
import com.gadgetmart.user.domain.User;
import com.gadgetmart.user.dto.PageResponse;
import com.gadgetmart.user.dto.UpdateProfileRequest;
import com.gadgetmart.user.dto.UserResponse;
import com.gadgetmart.user.exception.ApiException;
import com.gadgetmart.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository users;
    private final RefreshTokenService refreshTokens;

    @Transactional(readOnly = true)
    public UserResponse getById(UUID id) {
        return UserResponse.from(find(id));
    }

    @Transactional
    public UserResponse updateProfile(UUID id, UpdateProfileRequest req) {
        User user = find(id);
        user.setFullName(req.fullName().trim());
        user.setPhone(req.phone());
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by("createdAt").descending());
        return PageResponse.from(users.findAll(pageable).map(UserResponse::from));
    }

    @Transactional
    public UserResponse setRoles(UUID id, Set<Role> roles) {
        User user = find(id);
        user.setRoles(new HashSet<>(roles));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse setEnabled(UUID id, boolean enabled) {
        User user = find(id);
        user.setEnabled(enabled);
        if (!enabled) {
            refreshTokens.revokeAll(id);   // kill sessions; existing access tokens expire within minutes
        }
        return UserResponse.from(user);
    }

    private User find(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
    }
}
