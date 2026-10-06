package com.bicavi.security;

import com.bicavi.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

// Usa os beans REAIS de encoder/decoder do SecurityConfig, sem subir o Spring.
class TokenServiceTest {

    private static final String SECRET = "segredo-de-teste-com-mais-de-32-bytes!!";
    private static final String OTHER_SECRET = "outro-segredo-tambem-com-mais-de-32-bytes";

    private final SecurityConfig config = new SecurityConfig();

    @Test
    void issuedTokenIsAcceptedAndCarriesUserIdAndExpiration() {
        TokenService tokens = tokenService(SECRET, Duration.ofHours(24));

        Jwt jwt = decoder(SECRET).decode(tokens.issue(userWithId(7L)));

        assertThat(jwt.getSubject()).isEqualTo("7");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("bicavi");
        assertThat(jwt.getExpiresAt()).isCloseTo(Instant.now().plus(Duration.ofHours(24)), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void payloadIsReadableByAnyoneSoItMustNotContainSecrets() {
        String token = tokenService(SECRET, Duration.ofHours(24)).issue(userWithId(7L));

        // As 3 partes: cabeçalho.conteúdo.assinatura. O conteúdo é só Base64.
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));

        assertThat(payload).contains("\"sub\":\"7\"").doesNotContain("password").doesNotContain("@");
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = tokenService(SECRET, Duration.ofHours(24)).issue(userWithId(7L));
        String[] parts = token.split("\\.");
        // Atacante troca o sub de 7 para 8, mas não consegue refazer a assinatura.
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                new String(Base64.getUrlDecoder().decode(parts[1])).replace("\"sub\":\"7\"", "\"sub\":\"8\"").getBytes());
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThatThrownBy(() -> decoder(SECRET).decode(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String token = tokenService(OTHER_SECRET, Duration.ofHours(24)).issue(userWithId(7L));

        assertThatThrownBy(() -> decoder(SECRET).decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        // Relógio "parado" 1 hora atrás: o token é emitido nesse momento, com
        // validade de 30 min, então hoje ele está vencido há 30 min.
        // (O validador tolera até 60s de diferença de relógio, o "clock skew".)
        Clock oneHourAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC);
        String token = tokenService(SECRET, Duration.ofMinutes(30), oneHourAgo).issue(userWithId(7L));

        assertThatThrownBy(() -> decoder(SECRET).decode(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void weakSecretPreventsStartup() {
        assertThatThrownBy(() -> new JwtProperties("curto", Duration.ofHours(24)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void nonPositiveExpirationPreventsStartup() {
        assertThatThrownBy(() -> new JwtProperties(SECRET, Duration.ofHours(-1)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtProperties(SECRET, Duration.ZERO))
                .isInstanceOf(IllegalStateException.class);
    }

    private TokenService tokenService(String secret, Duration expiration) {
        return tokenService(secret, expiration, Clock.systemUTC());
    }

    private TokenService tokenService(String secret, Duration expiration, Clock clock) {
        JwtProperties properties = new JwtProperties(secret, expiration);
        return new TokenService(config.jwtEncoder(properties), properties, clock);
    }

    private JwtDecoder decoder(String secret) {
        return config.jwtDecoder(new JwtProperties(secret, Duration.ofHours(24)));
    }

    // O id é gerado pelo banco; aqui não há banco, então definimos via reflexão (só em teste).
    private static User userWithId(Long id) {
        User user = new User("luis@example.com", "$2a$10$hash", "Luis");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
