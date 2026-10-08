package com.bicavi.auth;

import com.bicavi.common.BusinessRuleException;
import com.bicavi.common.ConflictException;
import com.bicavi.common.TooManyRequestsException;
import com.bicavi.common.UnauthorizedException;
import com.bicavi.security.TokenService;
import com.bicavi.user.User;
import com.bicavi.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository users;

    @Mock
    private TokenService tokenService;

    // Mock: por padrão não faz nada (libera todas as tentativas).
    // O comportamento real do limite é testado em LoginRateLimiterTest.
    @Mock
    private LoginRateLimiter loginRateLimiter;

    // BCrypt de verdade, para os testes mostrarem como ele se comporta.
    // spy = objeto real, mas que o Mockito consegue "espionar" com verify().
    private final PasswordEncoder passwordEncoder = spy(new BCryptPasswordEncoder());

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, passwordEncoder, tokenService, loginRateLimiter);
    }

    @Test
    void loginCountsAttemptUsingNormalizedEmail() {
        when(users.findByEmail("luis@example.com")).thenReturn(Optional.empty());

        catchThrowable(() -> service.login(new LoginRequest(" LUIS@Example.com ", "errada")));

        // " LUIS@Example.com " e "luis@example.com" gastam o MESMO balde.
        verify(loginRateLimiter).tryConsume("luis@example.com");
    }

    @Test
    void loginOverTheLimitSkipsDatabaseAndBcrypt() {
        doThrow(new TooManyRequestsException("Muitas tentativas", Duration.ofMinutes(3)))
                .when(loginRateLimiter).tryConsume("luis@example.com");

        assertThatThrownBy(() -> service.login(new LoginRequest("luis@example.com", "senha-forte-123")))
                .isInstanceOf(TooManyRequestsException.class);
        verifyNoInteractions(users, tokenService);
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void loginWithCorrectPasswordReturnsBearerToken() {
        User user = new User("luis@example.com", passwordEncoder.encode("senha-forte-123"), "Luis");
        when(users.findByEmail("luis@example.com")).thenReturn(Optional.of(user));
        when(tokenService.issue(user)).thenReturn("token.jwt.assinado");
        when(tokenService.expiration()).thenReturn(Duration.ofHours(24));

        LoginResponse response = service.login(new LoginRequest(" LUIS@example.com ", "senha-forte-123"));

        assertThat(response.accessToken()).isEqualTo("token.jwt.assinado");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(86_400);
    }

    @Test
    void loginWithWrongPasswordAndWithUnknownEmailFailTheSameWay() {
        User user = new User("luis@example.com", passwordEncoder.encode("senha-forte-123"), "Luis");
        when(users.findByEmail("luis@example.com")).thenReturn(Optional.of(user));
        when(users.findByEmail("ninguem@example.com")).thenReturn(Optional.empty());

        Throwable wrongPassword = catchThrowable(() -> service.login(new LoginRequest("luis@example.com", "errada")));
        Throwable unknownEmail = catchThrowable(() -> service.login(new LoginRequest("ninguem@example.com", "errada")));

        assertThat(wrongPassword).isInstanceOf(UnauthorizedException.class);
        assertThat(unknownEmail).isInstanceOf(UnauthorizedException.class);
        assertThat(unknownEmail.getMessage()).isEqualTo(wrongPassword.getMessage());
        verify(tokenService, never()).issue(any());
    }

    @Test
    void loginWithUnknownEmailStillRunsBcryptToEqualizeResponseTime() {
        when(users.findByEmail("ninguem@example.com")).thenReturn(Optional.empty());

        catchThrowable(() -> service.login(new LoginRequest("ninguem@example.com", "qualquer")));

        verify(passwordEncoder).matches(eq("qualquer"), anyString());
    }

    @Test
    void registerStoresBcryptHashAndNeverThePassword() {
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = service.register(new RegisterRequest("  Luis@Example.COM ", "senha-forte-123", " Luis "));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        String hash = saved.getValue().getPasswordHash();

        assertThat(response.email()).isEqualTo("luis@example.com");
        assertThat(response.name()).isEqualTo("Luis");
        assertThat(hash).isNotEqualTo("senha-forte-123").startsWith("$2a$10$").hasSize(60);
        assertThat(passwordEncoder.matches("senha-forte-123", hash)).isTrue();
        assertThat(passwordEncoder.matches("senha-errada", hash)).isFalse();
    }

    @Test
    void samePasswordProducesDifferentHashesBecauseOfSalt() {
        String first = passwordEncoder.encode("senha-forte-123");
        String second = passwordEncoder.encode("senha-forte-123");

        // Hashes diferentes (salt aleatório)...
        assertThat(first).isNotEqualTo(second);
        // ...mas os dois conferem com a senha, porque o salt fica guardado dentro do hash.
        assertThat(passwordEncoder.matches("senha-forte-123", first)).isTrue();
        assertThat(passwordEncoder.matches("senha-forte-123", second)).isTrue();
    }

    @Test
    void registerRejectsEmailAlreadyInUseIgnoringCase() {
        when(users.existsByEmail("luis@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("LUIS@example.com", "senha-forte-123", "Luis")))
                .isInstanceOf(ConflictException.class);
        verify(users, never()).save(any());
    }

    @Test
    void registerRejectsPasswordLongerThan72Bytes() {
        // 40 caracteres "ç" = 80 bytes em UTF-8: passa no @Size(max = 72), mas não no BCrypt.
        String password = "ç".repeat(40);

        assertThatThrownBy(() -> service.register(new RegisterRequest("luis@example.com", password, "Luis")))
                .isInstanceOf(BusinessRuleException.class);
    }
}
