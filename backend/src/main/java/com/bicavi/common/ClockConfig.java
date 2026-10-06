package com.bicavi.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

// Um único relógio para a aplicação inteira. Quem precisa da hora atual recebe
// este Clock em vez de chamar Instant.now()/LocalDate.now() direto: assim os
// testes podem usar um relógio fixo ("hoje é 06/10/2026").
// Fuso de São Paulo: define o que é "hoje" e "este mês" para o usuário.
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("America/Sao_Paulo"));
    }
}
