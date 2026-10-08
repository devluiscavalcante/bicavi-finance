package com.bicavi.auth;

import com.bicavi.common.BusinessRuleException;
import com.bicavi.common.ConflictException;
import com.bicavi.common.NotFoundException;
import com.bicavi.common.UnauthorizedException;
import com.bicavi.security.TokenService;
import com.bicavi.user.User;
import com.bicavi.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

    // O BCrypt só considera os primeiros 72 BYTES da senha. Letras acentuadas
    // ocupam 2 bytes em UTF-8, então 72 caracteres podem passar desse limite.
    private static final int BCRYPT_MAX_BYTES = 72;

    // Mesma mensagem para "e-mail não existe" e "senha errada":
    // não revelamos quais e-mails têm conta.
    private static final String INVALID_CREDENTIALS = "E-mail ou senha incorretos";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final LoginRateLimiter loginRateLimiter;

    // Hash de uma senha qualquer, usado quando o e-mail não existe (ver login).
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokenService,
                       LoginRateLimiter loginRateLimiter) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.loginRateLimiter = loginRateLimiter;
        this.dummyHash = passwordEncoder.encode("senha-ficticia-para-equalizar-tempo");
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

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        // Antes de tudo: com o limite estourado, nem consulta o banco nem roda o BCrypt.
        // Vale igual para e-mail existente ou não, então o 429 não revela quem tem conta.
        loginRateLimiter.tryConsume(email);

        Optional<User> user = users.findByEmail(email);

        if (user.isEmpty()) {
            // Roda o BCrypt mesmo assim, para a resposta demorar o mesmo tempo
            // que uma senha errada. Senão, medir o tempo revelaria se o e-mail existe.
            passwordEncoder.matches(request.password(), dummyHash);
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(request.password(), user.get().getPasswordHash())) {
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }

        String token = tokenService.issue(user.get());
        return new LoginResponse(token, "Bearer", tokenService.expiration().toSeconds());
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        return users.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
