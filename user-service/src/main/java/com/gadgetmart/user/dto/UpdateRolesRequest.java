package com.gadgetmart.user.dto;

import com.gadgetmart.user.domain.Role;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record UpdateRolesRequest(@NotEmpty Set<Role> roles) {
}
