package com.bicavi.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// Lê bicavi.login-limit.* do application.properties.
//   maxAttempts  tamanho do "balde": tentativas seguidas permitidas
//   refillEvery  a cada quanto tempo 1 tentativa volta para o balde
@ConfigurationProperties("bicavi.login-limit")
public record LoginLimitProperties(int maxAttempts, Duration refillEvery) {

    public LoginLimitProperties {
        if (maxAttempts < 1) {
            throw new IllegalStateException("bicavi.login-limit.max-attempts precisa ser pelo menos 1");
        }
        if (refillEvery == null || refillEvery.isNegative() || refillEvery.isZero()) {
            throw new IllegalStateException("bicavi.login-limit.refill-every precisa ser positivo");
        }
    }
}
