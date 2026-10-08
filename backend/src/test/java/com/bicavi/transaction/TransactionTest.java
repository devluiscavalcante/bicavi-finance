package com.bicavi.transaction;

import com.bicavi.category.Category;
import com.bicavi.common.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static com.bicavi.transaction.TestTransactions.expense;
import static com.bicavi.transaction.TestTransactions.income;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Teste unitário das regras da entidade: não precisa de Spring nem de banco.
class TransactionTest {

    private static final Long USER = 1L;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);
    private final Category groceries = new Category(USER, "Mercado", TransactionType.EXPENSE);

    @Test
    void createsValidTransaction() {
        Transaction tx = expense("35.90").category(groceries).description("Feira").build();

        assertThat(tx.getAmount()).isEqualByComparingTo("35.90");
        assertThat(tx.getCreatedAt()).isNotNull();
    }

    @Test
    void allowsTransactionWithoutCategory() {
        Transaction tx = expense("10").build();

        assertThat(tx.getCategory()).isNull();
    }

    @Test
    void rejectsZeroOrNegativeAmount() {
        assertThatThrownBy(() -> expense(BigDecimal.ZERO).category(groceries).build())
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> expense("-5").category(groceries).build())
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsMoreThanTwoDecimalPlaces() {
        assertThatThrownBy(() -> expense("10.555").category(groceries).build())
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("2 casas decimais");
    }

    @Test
    void refusesCategoryOfAnotherUserAsProgrammingError() {
        Category someoneElses = new Category(2L, "Mercado", TransactionType.EXPENSE);

        // IllegalStateException (bug nosso), não BusinessRuleException (erro do cliente):
        // o service nunca deveria chegar a passar uma categoria alheia.
        assertThatThrownBy(() -> expense("10").category(someoneElses).build())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsExpenseWithoutPaymentMethod() {
        assertThatThrownBy(() -> expense("10").category(groceries).payment(null).build())
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("forma de pagamento");
    }

    @Test
    void allowsIncomeWithoutPaymentMethod() {
        Transaction tx = income("5000").description("Salário").build();

        assertThat(tx.getPaymentMethod()).isNull();
    }

    @Test
    void rejectsIncomeWithPaymentMethod() {
        assertThatThrownBy(() -> income("5000").payment(PaymentMethod.PIX).build())
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Receitas");
    }

    @Test
    void updateAppliesPaymentMethodRuleToo() {
        Transaction tx = expense("10").build();

        assertThatThrownBy(() -> tx.update(null, new BigDecimal("10"), TransactionType.EXPENSE, null, null, null, TODAY))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsTypeDifferentFromCategoryType() {
        assertThatThrownBy(() -> income("10").category(groceries).build())
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Mercado");
    }
}
