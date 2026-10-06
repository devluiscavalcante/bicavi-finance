package com.bicavi.transaction;

import com.bicavi.category.Category;
import com.bicavi.common.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Teste unitário das regras da entidade: não precisa de Spring nem de banco.
class TransactionTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);
    private final Category groceries = new Category("Mercado", TransactionType.EXPENSE);

    @Test
    void createsValidTransaction() {
        Transaction tx = new Transaction(groceries, new BigDecimal("35.90"), TransactionType.EXPENSE, "Feira", TODAY);

        assertThat(tx.getAmount()).isEqualByComparingTo("35.90");
        assertThat(tx.getCreatedAt()).isNotNull();
    }

    @Test
    void allowsTransactionWithoutCategory() {
        Transaction tx = new Transaction(null, new BigDecimal("10"), TransactionType.EXPENSE, null, TODAY);

        assertThat(tx.getCategory()).isNull();
    }

    @Test
    void rejectsZeroOrNegativeAmount() {
        assertThatThrownBy(() -> new Transaction(groceries, BigDecimal.ZERO, TransactionType.EXPENSE, null, TODAY))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> new Transaction(groceries, new BigDecimal("-5"), TransactionType.EXPENSE, null, TODAY))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsMoreThanTwoDecimalPlaces() {
        assertThatThrownBy(() -> new Transaction(groceries, new BigDecimal("10.555"), TransactionType.EXPENSE, null, TODAY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("2 casas decimais");
    }

    @Test
    void rejectsTypeDifferentFromCategoryType() {
        assertThatThrownBy(() -> new Transaction(groceries, new BigDecimal("10"), TransactionType.INCOME, null, TODAY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Mercado");
    }
}
