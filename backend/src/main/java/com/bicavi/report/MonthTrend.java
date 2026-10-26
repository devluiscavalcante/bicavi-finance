package com.bicavi.report;

import java.math.BigDecimal;
import java.time.YearMonth;

// Um ponto do gráfico de evolução: os totais de um mês.
public record MonthTrend(
        YearMonth month,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal balance      // receitas - despesas (pode ser negativo)
) {
}
