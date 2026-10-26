package com.bicavi.report;

import com.bicavi.card.Card;
import com.bicavi.category.Category;
import com.bicavi.common.BusinessRuleException;
import com.bicavi.period.PeriodPolicy;
import com.bicavi.period.TestClocks;
import com.bicavi.transaction.MonthlyTotals;
import com.bicavi.transaction.PaymentMethod;
import com.bicavi.transaction.TestTransactions;
import com.bicavi.transaction.Transaction;
import com.bicavi.transaction.TransactionRepository;
import com.bicavi.transaction.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final Long USER = 1L;
    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final LocalDate DAY = LocalDate.of(2026, 10, 6);

    private final Category salary = new Category(USER, "Salário", TransactionType.INCOME);
    private final Category groceries = new Category(USER, "Mercado", TransactionType.EXPENSE);
    private final Category transport = new Category(USER, "Transporte", TransactionType.EXPENSE);
    private final Card nubank = new Card(USER, "Nubank");
    private final Card itau = new Card(USER, "Itaú");

    @Mock
    private TransactionRepository transactions;

    private ReportService service;

    // "Hoje" é 06/10/2026: outubro é o mês atual e a consulta vai até abril.
    @BeforeEach
    void createService() {
        service = new ReportService(transactions, new PeriodPolicy(TestClocks.at(DAY)));
    }

    @Test
    void rejectsMonthOlderThanTheConsultationWindow() {
        assertThatThrownBy(() -> service.monthlySummary(USER, YearMonth.of(2026, 3)))
                .isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(transactions);
    }

    @Test
    void calculatesTotalsAndBalance() {
        givenOctoberTransactions(
                income(salary, "5000.00"),
                expense(groceries, "350.50"),
                expense(transport, "120.00"));

        MonthlySummaryResponse summary = service.monthlySummary(USER, OCTOBER);

        assertThat(summary.totalIncome()).isEqualByComparingTo("5000.00");
        assertThat(summary.totalExpense()).isEqualByComparingTo("470.50");
        assertThat(summary.balance()).isEqualByComparingTo("4529.50");
    }

    @Test
    void balanceCanBeNegative() {
        givenOctoberTransactions(income(salary, "100.00"), expense(groceries, "150.00"));

        assertThat(service.monthlySummary(USER, OCTOBER).balance()).isEqualByComparingTo("-50.00");
    }

    @Test
    void groupsExpensesByCategoryFromHighestToLowest() {
        givenOctoberTransactions(
                expense(transport, "20.00"),
                expense(groceries, "100.00"),
                expense(transport, "30.00"),
                expense(null, "70.00"),
                income(salary, "5000.00")); // receita não entra em "gastos por categoria"

        List<CategoryTotal> byCategory = service.monthlySummary(USER, OCTOBER).expensesByCategory();

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

        assertThat(service.monthlySummary(USER, OCTOBER).totalExpense()).isEqualTo(new BigDecimal("0.30"));
    }

    @Test
    void emptyMonthReturnsZerosWithTwoDecimals() {
        givenOctoberTransactions();

        MonthlySummaryResponse summary = service.monthlySummary(USER, OCTOBER);

        // isEqualTo (e não isEqualByComparingTo) também confere a escala: "0.00", não "0"
        assertThat(summary.totalIncome()).isEqualTo(new BigDecimal("0.00"));
        assertThat(summary.totalExpense()).isEqualTo(new BigDecimal("0.00"));
        assertThat(summary.balance()).isEqualTo(new BigDecimal("0.00"));
        assertThat(summary.totalPaid()).isEqualTo(new BigDecimal("0.00"));
        assertThat(summary.totalToPay()).isEqualTo(new BigDecimal("0.00"));
        assertThat(summary.expensesByCategory()).isEmpty();
        assertThat(summary.expensesByCard()).isEmpty();
    }

    @Test
    void groupsCreditExpensesByCardFromHighestToLowest() {
        givenOctoberTransactions(
                credit(groceries, nubank, "100.00"),
                credit(transport, nubank, "30.00"),
                credit(groceries, itau, "50.00"),
                expense(groceries, "999.00"),       // Pix: não é de cartão
                income(salary, "5000.00"));

        List<CardTotal> byCard = service.monthlySummary(USER, OCTOBER).expensesByCard();

        assertThat(byCard).extracting(CardTotal::cardName).containsExactly("Nubank", "Itaú");
        assertThat(byCard).extracting(CardTotal::total)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("130.00"), new BigDecimal("50.00"));
        assertThat(byCard).extracting(CardTotal::purchases).containsExactly(2L, 1L);
    }

    // A fatura é outro AGRUPAMENTO das mesmas despesas, não uma despesa a mais:
    // o biscoito de R$ 8 no crédito aparece na categoria E na fatura, mas o
    // total do mês continua R$ 8.
    @Test
    void cardTotalsDoNotCountExpensesTwice() {
        givenOctoberTransactions(credit(groceries, nubank, "8.00"));

        MonthlySummaryResponse summary = service.monthlySummary(USER, OCTOBER);

        assertThat(summary.totalExpense()).isEqualByComparingTo("8.00");
        assertThat(summary.expensesByCategory()).singleElement()
                .satisfies(c -> assertThat(c.total()).isEqualByComparingTo("8.00"));
        assertThat(summary.expensesByCard()).singleElement()
                .satisfies(c -> assertThat(c.total()).isEqualByComparingTo("8.00"));
    }

    @Test
    void legacyCreditExpensesWithoutCardAppearAsTheirOwnInvoice() {
        // Compra no crédito de antes dos cartões: a entidade não deixa CRIAR uma
        // assim hoje, então simulamos a que vem do banco com um mock.
        Transaction legacy = mock(Transaction.class);
        when(legacy.getType()).thenReturn(TransactionType.EXPENSE);
        when(legacy.getPaymentMethod()).thenReturn(PaymentMethod.CREDITO);
        when(legacy.getAmount()).thenReturn(new BigDecimal("40.00"));
        givenOctoberTransactions(legacy, credit(groceries, nubank, "10.00"));

        List<CardTotal> byCard = service.monthlySummary(USER, OCTOBER).expensesByCard();

        assertThat(byCard).extracting(CardTotal::cardName).containsExactly("Crédito sem cartão", "Nubank");
        assertThat(byCard.get(0).cardId()).isNull();
    }

    @Test
    void queriesTheWholeMonth() {
        givenOctoberTransactions();

        service.monthlySummary(USER, OCTOBER);

        verify(transactions).findInPeriod(USER, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1), null);
    }

    private void givenOctoberTransactions(Transaction... txs) {
        when(transactions.findInPeriod(USER, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1), null))
                .thenReturn(List.of(txs));
    }

    private static Transaction income(Category category, String amount) {
        return TestTransactions.income(amount).category(category).build();
    }

    private static Transaction credit(Category category, Card card, String amount) {
        return TestTransactions.expense(amount).category(category).credit(card).build();
    }

    private static Transaction expense(Category category, String amount) {
        return TestTransactions.expense(amount).category(category).build();
    }

    @Test
    void splitsExpensesIntoPaidAndToPay() {
        givenOctoberTransactions(
                paid(expense(groceries, "100.00")),
                expense(transport, "30.00"),
                paid(credit(groceries, nubank, "50.00")),
                income(salary, "5000.00")); // receita não entra em pago nem em a pagar

        MonthlySummaryResponse summary = service.monthlySummary(USER, OCTOBER);

        assertThat(summary.totalPaid()).isEqualByComparingTo("150.00");
        assertThat(summary.totalToPay()).isEqualByComparingTo("30.00");
        // Pagar não muda o total do mês nem o saldo: a despesa continua existindo.
        assertThat(summary.totalExpense()).isEqualByComparingTo("180.00");
        assertThat(summary.balance()).isEqualByComparingTo("4820.00");
    }

    @Test
    void invoiceShowsWhatIsLeftToPay() {
        givenOctoberTransactions(
                paid(credit(groceries, nubank, "100.00")),
                credit(transport, nubank, "30.00"),
                paid(credit(groceries, itau, "50.00")));

        List<CardTotal> byCard = service.monthlySummary(USER, OCTOBER).expensesByCard();

        assertThat(byCard).extracting(CardTotal::toPay)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("30.00"), new BigDecimal("0.00"));  // Itaú: fatura paga
    }

    private static Transaction paid(Transaction tx) {
        tx.markPaid(DAY);
        return tx;
    }

    // --- Evolução mês a mês ---

    @Test
    void trendFillsMonthsWithoutTransactionsWithZeros() {
        YearMonth from = YearMonth.of(2026, 9);
        YearMonth to = YearMonth.of(2026, 11);
        when(transactions.sumByMonth(USER, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 1)))
                .thenReturn(List.of(
                        new MonthlyTotals(2026, 9, new BigDecimal("5000.00"), new BigDecimal("4200.00")),
                        // Outubro não veio do banco: nenhuma transação.
                        new MonthlyTotals(2026, 11, new BigDecimal("0"), new BigDecimal("300.00"))));

        List<MonthTrend> trend = service.monthlyTrend(USER, from, to);

        assertThat(trend).extracting(MonthTrend::month)
                .containsExactly(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11));
        assertThat(trend.get(0).balance()).isEqualByComparingTo("800.00");
        assertThat(trend.get(1).totalIncome()).isEqualTo(new BigDecimal("0.00"));
        assertThat(trend.get(1).balance()).isEqualTo(new BigDecimal("0.00"));
        // O "0" do banco vira "0.00", e o saldo pode ser negativo.
        assertThat(trend.get(2).totalIncome()).isEqualTo(new BigDecimal("0.00"));
        assertThat(trend.get(2).balance()).isEqualTo(new BigDecimal("-300.00"));
    }

    @Test
    void trendOfASingleMonthIsAllowed() {
        assertThat(service.monthlyTrend(USER, OCTOBER, OCTOBER)).hasSize(1);
    }

    @Test
    void trendRejectsFromAfterTo() {
        assertThatThrownBy(() -> service.monthlyTrend(USER, YearMonth.of(2026, 11), OCTOBER))
                .isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(transactions);
    }

    @Test
    void trendRejectsMonthOutsideTheConsultationWindow() {
        assertThatThrownBy(() -> service.monthlyTrend(USER, YearMonth.of(2026, 3), OCTOBER))
                .isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(transactions);
    }

    @Test
    void trendRejectsMoreThanTheMaximumOfMonths() {
        YearMonth from = YearMonth.of(2026, 4);

        assertThat(service.monthlyTrend(USER, from, from.plusMonths(ReportService.MAX_TREND_MONTHS - 1)))
                .hasSize(ReportService.MAX_TREND_MONTHS);
        assertThatThrownBy(() -> service.monthlyTrend(USER, from, from.plusMonths(ReportService.MAX_TREND_MONTHS)))
                .isInstanceOf(BusinessRuleException.class);
    }
}
