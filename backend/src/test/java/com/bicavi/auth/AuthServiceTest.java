package com.bicavi.auth;

import com.bicavi.common.BusinessRuleException;
import com.bicavi.common.ConflictException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository users;

    // BCrypt de verdade (não mock), para os testes mostrarem como ele se comporta.
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, passwordEncoder);
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
