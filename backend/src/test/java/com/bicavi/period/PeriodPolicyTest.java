package com.bicavi.period;

import com.bicavi.common.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Teste unitário puro: o Clock fixo define "hoje" em cada caso.
class PeriodPolicyTest {

    private static PeriodPolicy on(int year, int month, int day) {
        return new PeriodPolicy(TestClocks.at(LocalDate.of(year, month, day)));
    }

    @Test
    void previousMonthStaysOpenUntilDayFive() {
        assertThat(on(2026, 11, 5).firstEditableMonth()).isEqualTo(YearMonth.of(2026, 10));
    }

    @Test
    void previousMonthClosesOnDaySix() {
        assertThat(on(2026, 11, 6).firstEditableMonth()).isEqualTo(YearMonth.of(2026, 11));
    }

    @Test
    void toleranceCrossesTheYear() {
        assertThat(on(2027, 1, 3).firstEditableMonth()).isEqualTo(YearMonth.of(2026, 12));
    }

    @Test
    void dayFiveEndsAtMidnightInSaoPaulo() {
        // 02:00 UTC do dia 06/11 ainda é 23:00 do dia 05/11 em São Paulo (UTC-3):
        // a tolerância continua valendo. Com relógio em UTC, outubro já estaria fechado.
        Clock lateNight = Clock.fixed(Instant.parse("2026-11-06T02:00:00Z"), TestClocks.SAO_PAULO);

        assertThat(new PeriodPolicy(lateNight).firstEditableMonth()).isEqualTo(YearMonth.of(2026, 10));
    }

    @Test
    void currentAndFutureMonthsAreEditable() {
        PeriodPolicy policy = on(2026, 10, 15);

        assertThatCode(() -> policy.checkEditable(LocalDate.of(2026, 10, 1))).doesNotThrowAnyException();
        assertThatCode(() -> policy.checkEditable(LocalDate.of(2027, 3, 10))).doesNotThrowAnyException();
    }

    @Test
    void closedMonthIsRejectedWithClearMessage() {
        PeriodPolicy policy = on(2026, 10, 15);

        assertThatThrownBy(() -> policy.checkEditable(LocalDate.of(2026, 9, 30)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("O mês 09/2026 está fechado: só é possível alterar transações a partir de 10/2026");
    }

    @Test
    void canConsultUpToSixMonthsBack() {
        PeriodPolicy policy = on(2026, 10, 15);

        assertThat(policy.oldestVisibleMonth()).isEqualTo(YearMonth.of(2026, 4));
        assertThatCode(() -> policy.checkVisible(YearMonth.of(2026, 4))).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.checkVisible(YearMonth.of(2026, 3)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("04/2026");
    }

    @Test
    void futureMonthsAreAlwaysVisible() {
        assertThat(on(2026, 10, 15).isVisible(LocalDate.of(2030, 1, 1))).isTrue();
    }
}
