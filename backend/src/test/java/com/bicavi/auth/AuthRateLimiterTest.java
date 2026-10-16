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
// (ver application.properties) e um relógio que o teste avança.
class AuthRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-08T12:00:00Z"));
    private final AuthRateLimiter limiter = new AuthRateLimiter(new AuthLimitProperties(
            new RateLimit(5, Duration.ofMinutes(3)),     // login por e-mail
            new RateLimit(30, Duration.ofSeconds(2)),    // login global: 30 por minuto
            new RateLimit(5, Duration.ofMinutes(12))),   // cadastro: 5 por hora
            clock);

    @Test
    void allowsFiveAttemptsAndBlocksTheSixth() {
        for (int i = 0; i < 5; i++) {
            limiter.tryConsumeLogin("luis@example.com");
        }

        TooManyRequestsException blocked = catchThrowableOfType(TooManyRequestsException.class,
                () -> limiter.tryConsumeLogin("luis@example.com"));

        assertThat(blocked.getRetryAfter()).isEqualTo(Duration.ofMinutes(3));
        assertThat(blocked.getMessage()).isEqualTo("Muitas tentativas de login. Tente novamente em 3 minutos.");
    }

    @Test
    void oneAttemptComesBackAfterRefillPeriod() {
        exhaust("luis@example.com");

        clock.advance(Duration.ofMinutes(3));

        // Voltou exatamente 1 ficha: a primeira passa, a segunda não.
        assertThatCode(() -> limiter.tryConsumeLogin("luis@example.com")).doesNotThrowAnyException();
        TooManyRequestsException blocked = catchThrowableOfType(TooManyRequestsException.class,
                () -> limiter.tryConsumeLogin("luis@example.com"));
        assertThat(blocked).isNotNull();
    }

    @Test
    void retryAfterShrinksAsTimePasses() {
        exhaust("luis@example.com");

        clock.advance(Duration.ofMinutes(2));

        TooManyRequestsException blocked = catchThrowableOfType(TooManyRequestsException.class,
                () -> limiter.tryConsumeLogin("luis@example.com"));
        assertThat(blocked.getRetryAfter()).isEqualTo(Duration.ofMinutes(1));
        assertThat(blocked.getMessage()).endsWith("em 1 minuto.");
    }

    @Test
    void eachEmailHasItsOwnBucket() {
        exhaust("atacado@example.com");

        // Estourar o limite de um e-mail não bloqueia o login de outro.
        assertThatCode(() -> limiter.tryConsumeLogin("luis@example.com")).doesNotThrowAnyException();
    }

    @Test
    void globalLoginLimitBlocksLoopWithDifferentEmails() {
        // Um e-mail novo por tentativa: o limite por e-mail nunca estoura...
        for (int i = 0; i < 30; i++) {
            limiter.tryConsumeLogin("inventado" + i + "@example.com");
        }

        // ...mas o global sim, e barra qualquer e-mail (inclusive um de verdade).
        TooManyRequestsException blocked = catchThrowableOfType(TooManyRequestsException.class,
                () -> limiter.tryConsumeLogin("luis@example.com"));
        assertThat(blocked.getRetryAfter()).isEqualTo(Duration.ofSeconds(2));
        assertThat(blocked.getMessage()).isEqualTo("Muitas tentativas de login. Tente novamente em 2 segundos.");

        // 2 segundos depois, 1 ficha voltou ao balde global.
        clock.advance(Duration.ofSeconds(2));
        assertThatCode(() -> limiter.tryConsumeLogin("luis@example.com")).doesNotThrowAnyException();
    }

    @Test
    void attemptsBlockedByTheEmailLimitDoNotDrainTheGlobalOne() {
        exhaust("atacado@example.com");  // gasta 5 do global
        for (int i = 0; i < 20; i++) {
            catchThrowableOfType(TooManyRequestsException.class,
                    () -> limiter.tryConsumeLogin("atacado@example.com"));
        }

        // As 20 tentativas barradas pelo balde do e-mail não gastaram o global:
        // sobraram 25 fichas para os outros.
        for (int i = 0; i < 25; i++) {
            String email = "pessoa" + i + "@example.com";
            assertThatCode(() -> limiter.tryConsumeLogin(email)).doesNotThrowAnyException();
        }
    }

    @Test
    void allowsFiveRegistrationsAndBlocksTheSixth() {
        for (int i = 0; i < 5; i++) {
            limiter.tryConsumeRegister();
        }

        TooManyRequestsException blocked = catchThrowableOfType(TooManyRequestsException.class,
                limiter::tryConsumeRegister);
        assertThat(blocked.getRetryAfter()).isEqualTo(Duration.ofMinutes(12));
        assertThat(blocked.getMessage()).isEqualTo("Muitos cadastros em pouco tempo. Tente novamente em 12 minutos.");

        clock.advance(Duration.ofMinutes(12));
        assertThatCode(limiter::tryConsumeRegister).doesNotThrowAnyException();
    }

    @Test
    void registrationAndLoginLimitsAreIndependent() {
        for (int i = 0; i < 5; i++) {
            limiter.tryConsumeRegister();
        }

        // Cadastro esgotado não impede quem já tem conta de entrar.
        assertThatCode(() -> limiter.tryConsumeLogin("luis@example.com")).doesNotThrowAnyException();
    }

    private void exhaust(String email) {
        for (int i = 0; i < 5; i++) {
            limiter.tryConsumeLogin(email);
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
