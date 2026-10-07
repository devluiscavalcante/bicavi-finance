package com.bicavi.period;

import com.bicavi.common.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

// Quais meses podem ser alterados e quais podem ser consultados.
//
//  - Alterar (criar/editar/excluir): mês atual e TODOS os futuros (salários e
//    parcelas planejados). O mês anterior continua aberto até o dia 5, para
//    lançar o que ficou esquecido no fim do mês.
//  - Consultar: até 6 meses antes do atual. Nada é apagado; só não é devolvido.
//
// "Hoje" vem do Clock (fuso de São Paulo, ver ClockConfig): os testes fixam a data.
@Component
public class PeriodPolicy {

    // Até este dia (inclusive), o mês anterior ainda aceita alterações.
    static final int TOLERANCE_DAY = 5;
    // Quantos meses antes do atual ainda podem ser consultados.
    static final int VISIBLE_MONTHS = 6;

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MM/yyyy");

    private final Clock clock;

    public PeriodPolicy(Clock clock) {
        this.clock = clock;
    }

    public YearMonth firstEditableMonth() {
        LocalDate today = LocalDate.now(clock);
        YearMonth current = YearMonth.from(today);
        return today.getDayOfMonth() <= TOLERANCE_DAY ? current.minusMonths(1) : current;
    }

    public YearMonth oldestVisibleMonth() {
        return YearMonth.now(clock).minusMonths(VISIBLE_MONTHS);
    }

    public boolean isVisible(LocalDate date) {
        return !YearMonth.from(date).isBefore(oldestVisibleMonth());
    }

    public void checkEditable(LocalDate date) {
        YearMonth month = YearMonth.from(date);
        YearMonth first = firstEditableMonth();
        if (month.isBefore(first)) {
            throw new BusinessRuleException("O mês " + month.format(MONTH_FORMAT)
                    + " está fechado: só é possível alterar transações a partir de " + first.format(MONTH_FORMAT));
        }
    }

    public void checkVisible(YearMonth month) {
        if (month.isBefore(oldestVisibleMonth())) {
            throw new BusinessRuleException("Só é possível consultar a partir de "
                    + oldestVisibleMonth().format(MONTH_FORMAT));
        }
    }
}
