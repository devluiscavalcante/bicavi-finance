package com.bicavi.report;

import com.bicavi.category.Category;
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

    private final TransactionRepository transactions;

    public ReportService(TransactionRepository transactions) {
        this.transactions = transactions;
    }

    @Transactional(readOnly = true)
    public MonthlySummaryResponse monthlySummary(YearMonth month) {
        List<Transaction> all = transactions.findInPeriod(month.atDay(1), month.plusMonths(1).atDay(1), null);

        BigDecimal income = sum(all, TransactionType.INCOME);
        BigDecimal expense = sum(all, TransactionType.EXPENSE);

        return new MonthlySummaryResponse(
                month,
                income,
                expense,
                income.subtract(expense),
                expensesByCategory(all));
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

    private record CategoryKey(Long id, String name) {

        static CategoryKey of(Category category) {
            return category == null
                    ? new CategoryKey(null, UNCATEGORIZED)
                    : new CategoryKey(category.getId(), category.getName());
        }
    }
}
