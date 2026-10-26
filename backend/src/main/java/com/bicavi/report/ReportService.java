package com.bicavi.report;

import com.bicavi.card.Card;
import com.bicavi.category.Category;
import com.bicavi.common.BusinessRuleException;
import com.bicavi.period.PeriodPolicy;
import com.bicavi.transaction.MonthlyTotals;
import com.bicavi.transaction.PaymentMethod;
import com.bicavi.transaction.Transaction;
import com.bicavi.transaction.TransactionRepository;
import com.bicavi.transaction.TransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Cálculos financeiros determinísticos. A IA usará estes resultados,
// nunca fará as contas por conta própria.
//
// Decisão: buscamos as transações do mês e somamos em Java. Para o volume
// de uma pessoa física (dezenas/centenas por mês) é suficiente. Em escala,
// trocaríamos por SUM/GROUP BY no banco sem mudar a API.
@Service
public class ReportService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final String UNCATEGORIZED = "Sem categoria";
    private static final String CREDIT_WITHOUT_CARD = "Crédito sem cartão";
    // Limite do gráfico de evolução: o padrão da tela é 13 meses (6 + atual + 6).
    static final int MAX_TREND_MONTHS = 24;

    private final TransactionRepository transactions;
    private final PeriodPolicy period;

    public ReportService(TransactionRepository transactions, PeriodPolicy period) {
        this.transactions = transactions;
        this.period = period;
    }

    @Transactional(readOnly = true)
    public MonthlySummaryResponse monthlySummary(Long userId, YearMonth month) {
        period.checkVisible(month);
        List<Transaction> all = transactions.findInPeriod(userId, month.atDay(1), month.plusMonths(1).atDay(1), null);

        BigDecimal income = sum(all, TransactionType.INCOME);
        BigDecimal expense = sum(all, TransactionType.EXPENSE);
        BigDecimal toPay = sumUnpaid(all);

        return new MonthlySummaryResponse(
                month,
                income,
                expense,
                expense.subtract(toPay),
                toPay,
                income.subtract(expense),
                expensesByCategory(all),
                expensesByCard(all));
    }

    // Totais mês a mês, de "from" até "to" (inclusive), para o gráfico de evolução.
    //
    // Aqui a soma é feita no banco (GROUP BY), não em Java como no monthlySummary:
    // são vários meses de uma vez e só interessam os totais, não as transações.
    @Transactional(readOnly = true)
    public List<MonthTrend> monthlyTrend(Long userId, YearMonth from, YearMonth to) {
        period.checkVisible(from);
        period.checkVisible(to);
        if (from.isAfter(to)) {
            throw new BusinessRuleException("O mês inicial não pode ser depois do final");
        }
        long months = ChronoUnit.MONTHS.between(from, to) + 1;
        if (months > MAX_TREND_MONTHS) {
            throw new BusinessRuleException("Consulte no máximo " + MAX_TREND_MONTHS + " meses por vez");
        }

        Map<YearMonth, MonthlyTotals> byMonth = transactions
                .sumByMonth(userId, from.atDay(1), to.plusMonths(1).atDay(1))
                .stream()
                .collect(Collectors.toMap(row -> YearMonth.of(row.year(), row.month()), row -> row));

        // O banco só devolve meses que têm transações. Percorremos o intervalo
        // inteiro para que mês vazio apareça zerado (senão o gráfico "pula" meses).
        return Stream.iterate(from, month -> month.plusMonths(1))
                .limit(months)
                .map(month -> toTrend(month, byMonth.get(month)))
                .toList();
    }

    private static MonthTrend toTrend(YearMonth month, MonthlyTotals row) {
        if (row == null) {
            return new MonthTrend(month, ZERO, ZERO, ZERO);
        }
        // setScale(2): um SUM só de zeros (ELSE 0) volta como "0", sem casas decimais.
        BigDecimal income = row.income().setScale(2);
        BigDecimal expense = row.expense().setScale(2);
        return new MonthTrend(month, income, expense, income.subtract(expense));
    }

    private static BigDecimal sum(List<Transaction> all, TransactionType type) {
        return all.stream()
                .filter(tx -> tx.getType() == type)
                .map(Transaction::getAmount)
                .reduce(ZERO, BigDecimal::add);
    }

    // Despesas ainda não pagas. Receitas nunca têm pagamento, então ficam de fora.
    private static BigDecimal sumUnpaid(List<Transaction> all) {
        return sum(all.stream().filter(tx -> !tx.isPaid()).toList(), TransactionType.EXPENSE);
    }

    private static List<CategoryTotal> expensesByCategory(List<Transaction> all) {
        // groupingBy não aceita chave null. Por isso a chave é um record
        // (id + nome), que existe mesmo quando a transação não tem categoria.
        Map<CategoryKey, BigDecimal> totals = all.stream()
                .filter(tx -> tx.getType() == TransactionType.EXPENSE)
                .collect(Collectors.groupingBy(
                        tx -> CategoryKey.of(tx.getCategory()),
                        Collectors.reducing(ZERO, Transaction::getAmount, BigDecimal::add)));

        return totals.entrySet().stream()
                .map(entry -> new CategoryTotal(entry.getKey().id(), entry.getKey().name(), entry.getValue()))
                .sorted(Comparator.comparing(CategoryTotal::total).reversed()
                        .thenComparing(CategoryTotal::categoryName))
                .toList();
    }

    // Fatura de cada cartão = compras no crédito do mês com aquele cartão.
    // Funciona porque a compra no crédito é lançada na data de vencimento da
    // fatura (regra combinada): as compras do mês SÃO a fatura do mês.
    private static List<CardTotal> expensesByCard(List<Transaction> all) {
        Map<CardKey, List<Transaction>> byCard = all.stream()
                .filter(tx -> tx.getType() == TransactionType.EXPENSE)
                .filter(tx -> tx.getPaymentMethod() == PaymentMethod.CREDITO)
                .collect(Collectors.groupingBy(tx -> CardKey.of(tx.getCard())));

        return byCard.entrySet().stream()
                .map(entry -> new CardTotal(
                        entry.getKey().id(),
                        entry.getKey().name(),
                        sum(entry.getValue(), TransactionType.EXPENSE),
                        sumUnpaid(entry.getValue()),
                        entry.getValue().size()))
                .sorted(Comparator.comparing(CardTotal::total).reversed()
                        .thenComparing(CardTotal::cardName))
                .toList();
    }

    // Mesma ideia de CategoryKey: compra no crédito antiga, sem cartão, também vira uma chave.
    private record CardKey(Long id, String name) {

        static CardKey of(Card card) {
            return card == null
                    ? new CardKey(null, CREDIT_WITHOUT_CARD)
                    : new CardKey(card.getId(), card.getName());
        }
    }

    private record CategoryKey(Long id, String name) {

        static CategoryKey of(Category category) {
            return category == null
                    ? new CategoryKey(null, UNCATEGORIZED)
                    : new CategoryKey(category.getId(), category.getName());
        }
    }
}
