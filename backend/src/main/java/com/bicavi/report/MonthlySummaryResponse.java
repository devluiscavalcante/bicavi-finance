package com.bicavi.report;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record MonthlySummaryResponse(
        YearMonth month,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal balance,                     // receitas - despesas (pode ser negativo)
        List<CategoryTotal> expensesByCategory  // do maior gasto para o menor
) {
}
