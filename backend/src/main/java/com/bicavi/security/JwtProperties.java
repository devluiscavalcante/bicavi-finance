package com.bicavi.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

// Lê bicavi.jwt.secret e bicavi.jwt.expiration do application.properties.
@ConfigurationProperties("bicavi.jwt")
public record JwtProperties(String secret, Duration expiration) {

    // O algoritmo HS256 exige uma chave de pelo menos 256 bits (32 bytes).
    private static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        // Falha ao SUBIR a aplicação, e não no primeiro login, se o segredo for fraco.
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "bicavi.jwt.secret (JWT_SECRET) precisa ter pelo menos " + MIN_SECRET_BYTES + " bytes");
        }
        if (expiration == null || expiration.isNegative() || expiration.isZero()) {
            throw new IllegalStateException("bicavi.jwt.expiration (JWT_EXPIRATION) precisa ser positivo");
        }
    }
}
