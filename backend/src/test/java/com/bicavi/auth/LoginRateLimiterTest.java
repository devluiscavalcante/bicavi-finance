package com.bicavi.auth;

import com.bicavi.common.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

// Teste de unidade puro (sem Spring): o limitador com os números de produção
// (5 tentativas, 1 nova a cada 3 minutos) e um relógio que o teste avança.
class LoginRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-08T12:00:00Z"));
    private final LoginRateLimiter limiter =
            new LoginRateLimiter(new LoginLimitProperties(5, Duration.ofMinutes(3)), clock);

    @Test
    void allowsFiveAttemptsAndBlocksTheSixth() {
        for (int i = 0; i < 5; i++) {
            limiter.tryConsume("luis@example.com");
        }

        TooManyRequestsException blocked = catchThrowableOfType(TooManyRequestsException.class,
                () -> limiter.tryConsume("luis@example.com"));

        assertThat(blocked.getRetryAfter()).isEqualTo(Duration.ofMinutes(3));
        assertThat(blocked.getMessage()).isEqualTo("Muitas tentativas de login. Tente novamente em 3 minutos.");
    }

    @Test
    void oneAttemptComesBackAfterRefillPeriod() {
        exhaust("luis@example.com");

        clock.advance(Duration.ofMinutes(3));

        // Voltou exatamente 1 ficha: a primeira passa, a segunda não.
        assertThatCode(() -> limiter.tryConsume("luis@example.com")).doesNotThrowAnyException();
        TooManyRequestsException blocked = catchThrowableOfType(TooManyRequestsException.class,
                () -> limiter.tryConsume("luis@example.com"));
        assertThat(blocked).isNotNull();
    }

    @Test
    void retryAfterShrinksAsTimePasses() {
        exhaust("luis@example.com");

        clock.advance(Duration.ofMinutes(2));

        TooManyRequestsException blocked = catchThrowableOfType(TooManyRequestsException.class,
                () -> limiter.tryConsume("luis@example.com"));
        assertThat(blocked.getRetryAfter()).isEqualTo(Duration.ofMinutes(1));
        assertThat(blocked.getMessage()).endsWith("em 1 minuto.");
    }

    @Test
    void eachEmailHasItsOwnBucket() {
        exhaust("atacado@example.com");

        // Estourar o limite de um e-mail não bloqueia o login de outro.
        assertThatCode(() -> limiter.tryConsume("luis@example.com")).doesNotThrowAnyException();
    }

    private void exhaust(String email) {
        for (int i = 0; i < 5; i++) {
            limiter.tryConsume(email);
        }
    }

    // Relógio que o teste avança na mão: simula o tempo passando sem esperar de verdade.
    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("America/Sao_Paulo");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }
    }
}
