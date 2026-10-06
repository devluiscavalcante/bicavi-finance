package com.bicavi.transaction;

import com.bicavi.TestcontainersConfiguration;
import com.bicavi.category.Category;
import com.bicavi.category.CategoryRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class TransactionRepositoryTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 6);

    @Autowired
    private TransactionRepository transactions;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void savesAndReadsTransactionWithCategory() {
        Category groceries = categories.save(new Category("Mercado", TransactionType.EXPENSE));
        Long id = transactions.save(
                new Transaction(groceries, new BigDecimal("35.90"), TransactionType.EXPENSE, "Feira", DAY)).getId();
        flushAndClear();

        Transaction found = transactions.findById(id).orElseThrow();
        assertThat(found.getAmount()).isEqualByComparingTo("35.90");
        assertThat(found.getOccurredOn()).isEqualTo(DAY);
        assertThat(found.getCategory().getName()).isEqualTo("Mercado");
    }

    @Test
    void storesMoneyExactly() {
        // Em double, 0.1 + 0.2 = 0.30000000000000004. Com BigDecimal + NUMERIC, é exato.
        BigDecimal amount = new BigDecimal("0.1").add(new BigDecimal("0.2"));
        Long id = transactions.save(new Transaction(null, amount, TransactionType.EXPENSE, null, DAY)).getId();
        flushAndClear();

        BigDecimal inDb = jdbc.queryForObject("SELECT amount FROM transactions WHERE id = ?", BigDecimal.class, id);
        assertThat(inDb).isEqualByComparingTo("0.30");
    }

    @Test
    void deletingCategoryKeepsTransactionWithoutCategory() {
        Category groceries = categories.save(new Category("Mercado", TransactionType.EXPENSE));
        Long txId = transactions.save(
                new Transaction(groceries, new BigDecimal("50.00"), TransactionType.EXPENSE, null, DAY)).getId();
        flushAndClear();

        categories.deleteById(groceries.getId());
        flushAndClear();

        Transaction found = transactions.findById(txId).orElseThrow();
        assertThat(found.getCategory()).isNull();
        assertThat(found.getAmount()).isEqualByComparingTo("50.00");
        assertThat(found.getType()).isEqualTo(TransactionType.EXPENSE);
    }

    @Test
    void databaseRejectsNonPositiveAmount() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO transactions (amount, type, occurred_on, created_at)
                VALUES (0, 'EXPENSE', DATE '2026-10-06', now())
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsUnknownCategory() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO transactions (category_id, amount, type, occurred_on, created_at)
                VALUES (999999, 10, 'EXPENSE', DATE '2026-10-06', now())
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findInPeriodRespectsMonthBoundaries() {
        Category groceries = categories.save(new Category("Mercado", TransactionType.EXPENSE));
        saveExpense(groceries, "30/09", LocalDate.of(2026, 9, 30));
        saveExpense(groceries, "01/10", LocalDate.of(2026, 10, 1));
        saveExpense(groceries, "31/10", LocalDate.of(2026, 10, 31));
        saveExpense(groceries, "01/11", LocalDate.of(2026, 11, 1));
        flushAndClear();

        List<Transaction> october = transactions.findInPeriod(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1), null);

        // Ordenado da mais recente para a mais antiga
        assertThat(october).extracting(Transaction::getDescription).containsExactly("31/10", "01/10");
    }

    @Test
    void findInPeriodFiltersByCategoryAndIncludesUncategorizedWhenNoFilter() {
        Category groceries = categories.save(new Category("Mercado", TransactionType.EXPENSE));
        Category transport = categories.save(new Category("Transporte", TransactionType.EXPENSE));
        saveExpense(groceries, "mercado", DAY);
        saveExpense(transport, "uber", DAY);
        saveExpense(null, "sem categoria", DAY);
        flushAndClear();

        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 11, 1);

        assertThat(transactions.findInPeriod(start, end, transport.getId()))
                .extracting(Transaction::getDescription).containsExactly("uber");
        assertThat(transactions.findInPeriod(start, end, null))
                .extracting(Transaction::getDescription)
                .containsExactlyInAnyOrder("mercado", "uber", "sem categoria");
    }

    @Test
    void findInPeriodLoadsCategoriesInASingleQuery() {
        for (int i = 1; i <= 5; i++) {
            Category category = categories.save(new Category("Categoria " + i, TransactionType.EXPENSE));
            saveExpense(category, "gasto " + i, DAY);
        }
        flushAndClear();

        Statistics stats = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        List<Transaction> result = transactions.findInPeriod(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1), null);
        result.forEach(tx -> tx.getCategory().getName()); // acessa cada categoria

        // Sem o JOIN FETCH seriam 6 consultas: 1 para as transações + 1 por categoria (N+1).
        assertThat(result).hasSize(5);
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
    }

    private void saveExpense(Category category, String description, LocalDate day) {
        transactions.save(new Transaction(category, new BigDecimal("10.00"), TransactionType.EXPENSE, description, day));
    }

    // flush: envia ao banco os comandos SQL pendentes.
    // clear: esvazia o cache do JPA, forçando a próxima leitura a ir ao banco.
    // Sem o clear, o JPA devolveria o objeto que está em memória, e não
    // veríamos o efeito do ON DELETE SET NULL, que acontece só no banco.
    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
