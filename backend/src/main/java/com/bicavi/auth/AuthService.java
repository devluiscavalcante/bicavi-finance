package com.bicavi.auth;

import com.bicavi.common.BusinessRuleException;
import com.bicavi.common.ConflictException;
import com.bicavi.user.User;
import com.bicavi.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class AuthService {

    // O BCrypt só considera os primeiros 72 BYTES da senha. Letras acentuadas
    // ocupam 2 bytes em UTF-8, então 72 caracteres podem passar desse limite.
    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (request.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new BusinessRuleException("A senha é longa demais");
        }
        if (users.existsByEmail(email)) {
            throw new ConflictException("Este e-mail já está cadastrado");
        }

        String hash = passwordEncoder.encode(request.password());
        User saved = users.save(new User(email, hash, request.name().trim()));
        return UserResponse.from(saved);
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
