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

// Limita as tentativas de login e de cadastro (ver AuthLimitProperties).
//
// Algoritmo "token bucket": cada balde tem maxAttempts fichas. Cada tentativa
// gasta 1 ficha; a cada refillEvery, 1 ficha volta. Balde vazio = 429.
// Assim quem passou do limite espera um pouco, e não fica bloqueado para sempre.
//
// Três baldes:
//  - um por e-mail no login: contra força bruta (testar senhas de uma conta sem parar).
//    Só as tentativas erradas ficam contando: o login certo devolve a ficha (loginSucceeded).
//  - um global no login: com um e-mail diferente a cada tentativa, o limite por
//    e-mail nunca estoura, mas cada tentativa roda o BCrypt (lento de propósito).
//    Um loop assim ocuparia a CPU do servidor.
//  - um global no cadastro: cada cadastro roda o BCrypt e grava o usuário e as
//    categorias padrão. Sem limite, um loop encheria o banco.
//
// Por que não por IP: atrás do nginx (e do Tailscale), todas as requisições
// chegam com o mesmo IP, e um balde por IP seria um balde único. Os baldes globais
// têm a mesma contrapartida (um abuso também atrasa quem usa o app direito), mas
// limitam o estrago. Num deploy aberto na internet, com IPs reais repassados pelo
// proxy, o próximo passo seria trocar os globais por baldes por IP.
@Component
@EnableConfigurationProperties(AuthLimitProperties.class)
public class AuthRateLimiter {

    // Teto de baldes por e-mail em memória. Cada um ocupa poucas centenas de bytes,
    // então o teto é alto: ao lotar, o Caffeine descarta baldes, e um atacante que
    // mandasse muitos e-mails inventados poderia "zerar" o balde de uma conta real.
    private static final long MAX_BUCKETS = 100_000;

    private static final String LOGIN_MESSAGE = "Muitas tentativas de login.";
    private static final String REGISTER_MESSAGE = "Muitos cadastros em pouco tempo.";

    private final AuthLimitProperties properties;
    private final TimeMeter timeMeter;

    // Um HashMap comum cresceria para sempre (um e-mail inventado por requisição
    // esgotaria a memória). O Caffeine descarta o balde parado depois do tempo de
    // encher de novo: a partir daí ele já seria igual a um balde novo.
    private final Cache<String, Bucket> loginBuckets;
    private final Bucket loginGlobal;
    private final Bucket register;

    public AuthRateLimiter(AuthLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.timeMeter = new ClockTimeMeter(clock);
        RateLimit perEmail = properties.loginPerEmail();
        this.loginBuckets = Caffeine.newBuilder()
                .expireAfterAccess(perEmail.refillEvery().multipliedBy(perEmail.maxAttempts()))
                .maximumSize(MAX_BUCKETS)
                .build();
        this.loginGlobal = newBucket(properties.loginGlobal());
        this.register = newBucket(properties.register());
    }

    // Gasta uma tentativa de login do e-mail (já normalizado) ou lança 429.
    // O balde do e-mail vem ANTES do global: quem insiste numa única conta é
    // barrado pelo balde dela e não esgota o global, que vale para todos.
    public void tryConsumeLogin(String email) {
        consume(loginBuckets.get(email, key -> newBucket(properties.loginPerEmail())), LOGIN_MESSAGE);
        consume(loginGlobal, LOGIN_MESSAGE);
    }

    // Login certo: devolve a ficha que tryConsumeLogin gastou do balde do e-mail.
    // Assim só as senhas ERRADAS se acumulam. Sem isso, quem divide a conta (o
    // casal usa um login só, em vários aparelhos) gastaria as 5 fichas só entrando
    // e bloquearia o login dos dois. O global não devolve: ele protege a CPU, e
    // o login certo também rodou o BCrypt.
    // O Bucket4j nunca passa da capacidade, então devolver a mais não acumula fichas.
    public void loginSucceeded(String email) {
        Bucket bucket = loginBuckets.getIfPresent(email);
        if (bucket != null) {
            bucket.addTokens(1);
        }
    }

    // Gasta uma tentativa de cadastro ou lança 429.
    public void tryConsumeRegister() {
        consume(register, REGISTER_MESSAGE);
    }

    private static void consume(Bucket bucket, String message) {
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            Duration wait = Duration.ofNanos(probe.getNanosToWaitForRefill());
            throw new TooManyRequestsException(message + " Tente novamente em " + describe(wait) + ".", wait);
        }
    }

    // Arredonda para cima: "em 0 minutos" ou "em 0 segundos" não ajudaria ninguém.
    private static String describe(Duration wait) {
        if (wait.toSeconds() < 60) {
            long seconds = Math.max(1, (wait.toMillis() + 999) / 1000);
            return seconds + (seconds == 1 ? " segundo" : " segundos");
        }
        long minutes = (wait.toSeconds() + 59) / 60;
        return minutes + (minutes == 1 ? " minuto" : " minutos");
    }

    private Bucket newBucket(RateLimit limit) {
        // Greedy: a ficha volta aos poucos (1/3 a cada minuto, se refillEvery = 3m),
        // e não de uma vez no fim do período.
        Bandwidth bandwidth = Bandwidth.builder()
                .capacity(limit.maxAttempts())
                .refillGreedy(1, limit.refillEvery())
                .build();
        return Bucket.builder()
                .addLimit(bandwidth)
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
