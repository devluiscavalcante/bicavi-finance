package com.bicavi.transaction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InstallmentsTest {

    @Test
    void exactDivisionGivesEqualInstallments() {
        assertThat(Installments.split(new BigDecimal("1200.00"), 6))
                .containsExactly(amounts("200.00", "200.00", "200.00", "200.00", "200.00", "200.00"));
    }

    @Test
    void leftoverCentsGoToTheFirstInstallment() {
        assertThat(Installments.split(new BigDecimal("100.00"), 3))
                .containsExactly(amounts("33.34", "33.33", "33.33"));
    }

    @Test
    void leftoverCanBeMoreThanOneCent() {
        // 0,11 / 3 = 0,0366...: base 0,03 (truncado) e sobram 0,02 para a 1ª parcela.
        assertThat(Installments.split(new BigDecimal("0.11"), 3))
                .containsExactly(amounts("0.05", "0.03", "0.03"));
    }

    // O ponto principal: para qualquer total e quantidade, a soma bate centavo a centavo.
    @ParameterizedTest
    @CsvSource({"100.00, 3", "999.99, 7", "1234.56, 24", "0.02, 2", "50, 6", "1999.90, 12"})
    void installmentsAlwaysAddUpToTheTotal(String total, int count) {
        List<BigDecimal> parts = Installments.split(new BigDecimal(total), count);

        assertThat(parts).hasSize(count);
        assertThat(parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(total);
        assertThat(parts).allSatisfy(part -> assertThat(part.scale()).isEqualTo(2));
    }

    private static BigDecimal[] amounts(String... values) {
        return java.util.Arrays.stream(values).map(BigDecimal::new).toArray(BigDecimal[]::new);
    }
}
