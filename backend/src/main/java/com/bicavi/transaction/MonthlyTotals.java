package com.bicavi.transaction;

import java.math.BigDecimal;

// Uma linha do GROUP BY de TransactionRepository.sumByMonth: receitas e despesas
// de um mês. É uma projeção: o JPQL monta o record direto (SELECT new ...),
// sem carregar nenhuma entidade Transaction.
//
// year/month separados porque é o que o banco agrupa; quem usa monta o YearMonth.
public record MonthlyTotals(int year, int month, BigDecimal income, BigDecimal expense) {
}
