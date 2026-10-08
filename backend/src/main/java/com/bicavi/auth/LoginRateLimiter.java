package com.bicavi.auth;

import com.bicavi.common.TooManyRequestsException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

// Limita as tentativas de login por e-mail, contra força bruta (testar senhas sem parar).
//
// Algoritmo "token bucket": cada e-mail tem um balde com maxAttempts fichas.
// Cada tentativa gasta 1 ficha; a cada refillEvery, 1 ficha volta. Balde vazio = 429.
// Assim quem errou a senha algumas vezes espera minutos, e não fica bloqueado para sempre.
//
// A chave é o e-mail, não o IP: atrás do nginx, todas as requisições chegam com o
// mesmo IP, e um balde único deixaria qualquer um bloquear o login de todos.
// Toda tentativa conta (certa ou errada): a regra fica simples e o limite é folgado.
@Component
@EnableConfigurationProperties(LoginLimitProperties.class)
public class LoginRateLimiter {

    // Teto de baldes em memória. Cada um ocupa poucas centenas de bytes, então o
    // teto é alto: ao lotar, o Caffeine descarta baldes, e um atacante que mandasse
    // muitos e-mails inventados poderia "zerar" o balde de uma conta real.
    private static final long MAX_BUCKETS = 100_000;

    private final LoginLimitProperties properties;
    private final TimeMeter timeMeter;

    // Um HashMap comum cresceria para sempre (um e-mail inventado por requisição
    // esgotaria a memória). O Caffeine descarta o balde parado depois do tempo de
    // encher de novo: a partir daí ele já seria igual a um balde novo.
    private final Cache<String, Bucket> buckets;

    public LoginRateLimiter(LoginLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.timeMeter = new ClockTimeMeter(clock);
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(properties.refillEvery().multipliedBy(properties.maxAttempts()))
                .maximumSize(MAX_BUCKETS)
                .build();
    }

    // Gasta uma tentativa do e-mail (já normalizado) ou lança 429 se acabaram.
    public void tryConsume(String email) {
        Bucket bucket = buckets.get(email, key -> newBucket());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            Duration wait = Duration.ofNanos(probe.getNanosToWaitForRefill());
            long minutes = Math.max(1, (wait.toSeconds() + 59) / 60);  // arredonda para cima
            throw new TooManyRequestsException(
                    "Muitas tentativas de login. Tente novamente em " + minutes
                            + (minutes == 1 ? " minuto." : " minutos."),
                    wait);
        }
    }

    private Bucket newBucket() {
        // Greedy: a ficha volta aos poucos (1/3 a cada minuto, se refillEvery = 3m),
        // e não de uma vez no fim do período.
        Bandwidth limit = Bandwidth.builder()
                .capacity(properties.maxAttempts())
                .refillGreedy(1, properties.refillEvery())
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .withCustomTimePrecision(timeMeter)
                .build();
    }

    // O Bucket4j mede o tempo pelo relógio do sistema. Aqui ele usa o Clock da
    // aplicação (ver ClockConfig), para os testes poderem "avançar o tempo".
    private record ClockTimeMeter(Clock clock) implements TimeMeter {

        @Override
        public long currentTimeNanos() {
            Instant now = clock.instant();
            return now.getEpochSecond() * 1_000_000_000L + now.getNano();
        }

        @Override
        public boolean isWallClockBased() {
            return true;
        }
    }
}
