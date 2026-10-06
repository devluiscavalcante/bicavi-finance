package com.bicavi.auth;

import com.bicavi.user.User;

// Nunca inclui o passwordHash: nem o hash deve sair da API.
public record UserResponse(Long id, String email, String name) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName());
    }
}
