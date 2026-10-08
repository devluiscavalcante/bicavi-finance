package com.bicavi.common;

import java.time.Duration;

// Limite de tentativas atingido. Vira HTTP 429, com o header Retry-After
// dizendo ao cliente quanto tempo esperar.
public class TooManyRequestsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyRequestsException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
