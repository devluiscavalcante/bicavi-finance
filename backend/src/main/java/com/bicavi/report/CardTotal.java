package com.bicavi.report;

import java.math.BigDecimal;

// Fatura de um cartão no mês: a soma das compras no crédito com aquele cartão.
// Não é uma despesa a mais, e sim outro agrupamento das MESMAS despesas (já
// contadas em totalExpense e em expensesByCategory).
// cardId == null representa as compras no crédito antigas, sem cartão.
// toPay = soma das compras ainda não pagas (zero = fatura paga).
public record CardTotal(Long cardId, String cardName, BigDecimal total, BigDecimal toPay, long purchases) {
}
