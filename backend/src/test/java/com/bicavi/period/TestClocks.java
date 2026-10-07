package com.bicavi.period;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

// Relógios fixos para testes: "hoje é tal dia", no fuso da aplicação.
public final class TestClocks {

    public static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private TestClocks() {
    }

    // Meio-dia: longe da virada do dia, então o fuso não interfere no resultado.
    public static Clock at(LocalDate today) {
        return Clock.fixed(today.atTime(LocalTime.NOON).atZone(SAO_PAULO).toInstant(), SAO_PAULO);
    }
}
