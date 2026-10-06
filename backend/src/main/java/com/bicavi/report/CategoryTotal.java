package com.bicavi.report;

import java.math.BigDecimal;

// categoryId == null representa as transações "Sem categoria".
public record CategoryTotal(Long categoryId, String categoryName, BigDecimal total) {
}
