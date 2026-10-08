package com.bicavi.report;

import com.bicavi.card.Card;
import com.bicavi.category.Category;
import com.bicavi.period.PeriodPolicy;
import com.bicavi.transaction.PaymentMethod;
import com.bicavi.transaction.Transaction;
import com.bicavi.transaction.TransactionRepository;
import com.bicavi.transaction.TransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

        return new MonthlySummaryResponse(
                month,
                income,
                expense,
                income.subtract(expense),
                expensesByCategory(all),
                expensesByCard(all));
    }

    private static BigDecimal sum(List<Transaction> all, TransactionType type) {
        return all.stream()
                .filter(tx -> tx.getType() == type)
                .map(Transaction::getAmount)
                .reduce(ZERO, BigDecimal::add);
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
                        entry.getValue().stream().map(Transaction::getAmount).reduce(ZERO, BigDecimal::add),
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
