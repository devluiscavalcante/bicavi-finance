package com.bicavi.report;

import com.bicavi.category.Category;
import com.bicavi.transaction.Transaction;
import com.bicavi.transaction.TransactionRepository;
import com.bicavi.transaction.TransactionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final LocalDate DAY = LocalDate.of(2026, 10, 6);

    private final Category salary = new Category("Salário", TransactionType.INCOME);
    private final Category groceries = new Category("Mercado", TransactionType.EXPENSE);
    private final Category transport = new Category("Transporte", TransactionType.EXPENSE);

    @Mock
    private TransactionRepository transactions;

    @InjectMocks
    private ReportService service;

    @Test
    void calculatesTotalsAndBalance() {
        givenOctoberTransactions(
                income(salary, "5000.00"),
                expense(groceries, "350.50"),
                expense(transport, "120.00"));

        MonthlySummaryResponse summary = service.monthlySummary(OCTOBER);

        assertThat(summary.totalIncome()).isEqualByComparingTo("5000.00");
        assertThat(summary.totalExpense()).isEqualByComparingTo("470.50");
        assertThat(summary.balance()).isEqualByComparingTo("4529.50");
    }

    @Test
    void balanceCanBeNegative() {
        givenOctoberTransactions(income(salary, "100.00"), expense(groceries, "150.00"));

        assertThat(service.monthlySummary(OCTOBER).balance()).isEqualByComparingTo("-50.00");
    }

    @Test
    void groupsExpensesByCategoryFromHighestToLowest() {
        givenOctoberTransactions(
                expense(transport, "20.00"),
                expense(groceries, "100.00"),
                expense(transport, "30.00"),
                expense(null, "70.00"),
                income(salary, "5000.00")); // receita não entra em "gastos por categoria"

        List<CategoryTotal> byCategory = service.monthlySummary(OCTOBER).expensesByCategory();

        assertThat(byCategory).extracting(CategoryTotal::categoryName)
                .containsExactly("Mercado", "Sem categoria", "Transporte");
        assertThat(byCategory).extracting(CategoryTotal::total)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("100.00"), new BigDecimal("70.00"), new BigDecimal("50.00"));
        assertThat(byCategory.get(1).categoryId()).isNull();
    }

    @Test
    void sumsCentsExactly() {
        // Com double: 0.1 + 0.2 = 0.30000000000000004
        givenOctoberTransactions(expense(groceries, "0.10"), expense(groceries, "0.20"));

        assertThat(service.monthlySummary(OCTOBER).totalExpense()).isEqualTo(new BigDecimal("0.30"));
    }

    @Test
    void emptyMonthReturnsZerosWithTwoDecimals() {
        givenOctoberTransactions();

        MonthlySummaryResponse summary = service.monthlySummary(OCTOBER);

        // isEqualTo (e não isEqualByComparingTo) também confere a escala: "0.00", não "0"
        assertThat(summary.totalIncome()).isEqualTo(new BigDecimal("0.00"));
        assertThat(summary.totalExpense()).isEqualTo(new BigDecimal("0.00"));
        assertThat(summary.balance()).isEqualTo(new BigDecimal("0.00"));
        assertThat(summary.expensesByCategory()).isEmpty();
    }

    @Test
    void queriesTheWholeMonth() {
        givenOctoberTransactions();

        service.monthlySummary(OCTOBER);

        verify(transactions).findInPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1), null);
    }

    private void givenOctoberTransactions(Transaction... txs) {
        when(transactions.findInPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1), null))
                .thenReturn(List.of(txs));
    }

    private static Transaction income(Category category, String amount) {
        return new Transaction(category, new BigDecimal(amount), TransactionType.INCOME, null, DAY);
    }

    private static Transaction expense(Category category, String amount) {
        return new Transaction(category, new BigDecimal(amount), TransactionType.EXPENSE, null, DAY);
    }
}
