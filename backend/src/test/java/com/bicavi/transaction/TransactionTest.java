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

    private static final Long USER = 1L;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);
    private final Category groceries = new Category(USER, "Mercado", TransactionType.EXPENSE);

    @Test
    void createsValidTransaction() {
        Transaction tx = new Transaction(USER, groceries, new BigDecimal("35.90"), TransactionType.EXPENSE, PaymentMethod.PIX, "Feira", TODAY);

        assertThat(tx.getAmount()).isEqualByComparingTo("35.90");
        assertThat(tx.getCreatedAt()).isNotNull();
    }

    @Test
    void allowsTransactionWithoutCategory() {
        Transaction tx = new Transaction(USER, null, new BigDecimal("10"), TransactionType.EXPENSE, PaymentMethod.PIX, null, TODAY);

        assertThat(tx.getCategory()).isNull();
    }

    @Test
    void rejectsZeroOrNegativeAmount() {
        assertThatThrownBy(() -> new Transaction(USER, groceries, BigDecimal.ZERO, TransactionType.EXPENSE, PaymentMethod.PIX, null, TODAY))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> new Transaction(USER, groceries, new BigDecimal("-5"), TransactionType.EXPENSE, PaymentMethod.PIX, null, TODAY))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsMoreThanTwoDecimalPlaces() {
        assertThatThrownBy(() -> new Transaction(USER, groceries, new BigDecimal("10.555"), TransactionType.EXPENSE, PaymentMethod.PIX, null, TODAY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("2 casas decimais");
    }

    @Test
    void refusesCategoryOfAnotherUserAsProgrammingError() {
        Category someoneElses = new Category(2L, "Mercado", TransactionType.EXPENSE);

        // IllegalStateException (bug nosso), não BusinessRuleException (erro do cliente):
        // o service nunca deveria chegar a passar uma categoria alheia.
        assertThatThrownBy(() -> new Transaction(USER, someoneElses, new BigDecimal("10"), TransactionType.EXPENSE, PaymentMethod.PIX, null, TODAY))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsExpenseWithoutPaymentMethod() {
        assertThatThrownBy(() -> new Transaction(USER, groceries, new BigDecimal("10"), TransactionType.EXPENSE, null, null, TODAY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("forma de pagamento");
    }

    @Test
    void allowsIncomeWithoutPaymentMethod() {
        Transaction tx = new Transaction(USER, null, new BigDecimal("5000"), TransactionType.INCOME, null, "Salário", TODAY);

        assertThat(tx.getPaymentMethod()).isNull();
    }

    @Test
    void rejectsIncomeWithPaymentMethod() {
        assertThatThrownBy(() -> new Transaction(USER, null, new BigDecimal("5000"), TransactionType.INCOME, PaymentMethod.PIX, null, TODAY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Receitas");
    }

    @Test
    void updateAppliesPaymentMethodRuleToo() {
        Transaction tx = new Transaction(USER, null, new BigDecimal("10"), TransactionType.EXPENSE, PaymentMethod.PIX, null, TODAY);

        assertThatThrownBy(() -> tx.update(null, new BigDecimal("10"), TransactionType.EXPENSE, null, null, TODAY))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsTypeDifferentFromCategoryType() {
        assertThatThrownBy(() -> new Transaction(USER, groceries, new BigDecimal("10"), TransactionType.INCOME, null, null, TODAY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Mercado");
    }
}
