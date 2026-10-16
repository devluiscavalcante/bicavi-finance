package com.bicavi.auth;

import java.time.Duration;

// Um limite "token bucket" (ver AuthRateLimiter):
//   maxAttempts  tamanho do "balde": tentativas seguidas permitidas
//   refillEvery  a cada quanto tempo 1 tentativa volta para o balde
public record RateLimit(int maxAttempts, Duration refillEvery) {

    public RateLimit {
        // Falha ao SUBIR a aplicação se a configuração não fizer sentido.
        if (maxAttempts < 1) {
            throw new IllegalStateException("max-attempts precisa ser pelo menos 1");
        }
        if (refillEvery == null || refillEvery.isNegative() || refillEvery.isZero()) {
            throw new IllegalStateException("refill-every precisa ser positivo");
        }
    }
}
