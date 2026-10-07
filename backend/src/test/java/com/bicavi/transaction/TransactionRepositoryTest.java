package com.bicavi.transaction;

import com.bicavi.TestcontainersConfiguration;
import com.bicavi.category.Category;
import com.bicavi.category.CategoryRepository;
import com.bicavi.user.User;
import com.bicavi.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
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
    private static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);
    private static final LocalDate NOV_1 = LocalDate.of(2026, 11, 1);

    @Autowired
    private TransactionRepository transactions;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private UserRepository users;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    private Long alice;
    private Long bob;

    @BeforeEach
    void createUsers() {
        alice = users.save(new User("alice@example.com", "$2a$10$hash", "Alice")).getId();
        bob = users.save(new User("bob@example.com", "$2a$10$hash", "Bob")).getId();
    }

    @Test
    void savesAndReadsTransactionWithCategory() {
        Category groceries = categories.save(new Category(alice, "Mercado", TransactionType.EXPENSE));
        Long id = transactions.save(
                new Transaction(alice, groceries, new BigDecimal("35.90"), TransactionType.EXPENSE, PaymentMethod.PIX, "Feira", DAY)).getId();
        flushAndClear();

        Transaction found = transactions.findByIdAndUserId(id, alice).orElseThrow();
        assertThat(found.getAmount()).isEqualByComparingTo("35.90");
        assertThat(found.getOccurredOn()).isEqualTo(DAY);
        assertThat(found.getCategory().getName()).isEqualTo("Mercado");
    }

    @Test
    void doesNotFindTransactionOfAnotherUser() {
        Long alicesTx = saveExpense(alice, null, "da Alice", DAY);
        flushAndClear();

        assertThat(transactions.findByIdAndUserId(alicesTx, bob)).isEmpty();
    }

    @Test
    void storesMoneyExactly() {
        // Em double, 0.1 + 0.2 = 0.30000000000000004. Com BigDecimal + NUMERIC, é exato.
        BigDecimal amount = new BigDecimal("0.1").add(new BigDecimal("0.2"));
        Long id = transactions.save(new Transaction(alice, null, amount, TransactionType.EXPENSE, PaymentMethod.PIX, null, DAY)).getId();
        flushAndClear();

        BigDecimal inDb = jdbc.queryForObject("SELECT amount FROM transactions WHERE id = ?", BigDecimal.class, id);
        assertThat(inDb).isEqualByComparingTo("0.30");
    }

    @Test
    void deletingCategoryKeepsTransactionWithoutCategory() {
        Category groceries = categories.save(new Category(alice, "Mercado", TransactionType.EXPENSE));
        Long txId = saveExpense(alice, groceries, null, DAY);
        flushAndClear();

        categories.deleteById(groceries.getId());
        flushAndClear();

        Transaction found = transactions.findByIdAndUserId(txId, alice).orElseThrow();
        assertThat(found.getCategory()).isNull();
        assertThat(found.getAmount()).isEqualByComparingTo("10.00");
        assertThat(found.getType()).isEqualTo(TransactionType.EXPENSE);
    }

    @Test
    void databaseRejectsNonPositiveAmount() {
        // user_id válido: o banco deve recusar por causa do valor, não por falta de dono.
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO transactions (user_id, amount, type, payment_method, occurred_on, created_at)
                VALUES (?, 0, 'EXPENSE', 'PIX', DATE '2026-10-06', now())
                """, alice))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_transactions_amount_positive");
    }

    @Test
    void databaseRejectsUnknownCategory() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO transactions (user_id, category_id, amount, type, payment_method, occurred_on, created_at)
                VALUES (?, 999999, 10, 'EXPENSE', 'PIX', DATE '2026-10-06', now())
                """, alice))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_transactions_category");
    }

    @Test
    void savesAndReadsPaymentMethod() {
        Long id = transactions.save(new Transaction(
                alice, null, new BigDecimal("30.00"), TransactionType.EXPENSE, PaymentMethod.CREDITO, "Caderno", DAY)).getId();
        flushAndClear();

        // Grava o NOME do enum (EnumType.STRING), não a posição (0, 1, 2...).
        String inDb = jdbc.queryForObject("SELECT payment_method FROM transactions WHERE id = ?", String.class, id);
        assertThat(inDb).isEqualTo("CREDITO");
        assertThat(transactions.findByIdAndUserId(id, alice).orElseThrow().getPaymentMethod())
                .isEqualTo(PaymentMethod.CREDITO);
    }

    @Test
    void databaseRejectsExpenseWithoutPaymentMethod() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO transactions (user_id, amount, type, occurred_on, created_at)
                VALUES (?, 10, 'EXPENSE', DATE '2026-10-06', now())
                """, alice))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_transactions_payment_method_by_type");
    }

    @Test
    void databaseRejectsIncomeWithPaymentMethod() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO transactions (user_id, amount, type, payment_method, occurred_on, created_at)
                VALUES (?, 5000, 'INCOME', 'PIX', DATE '2026-10-06', now())
                """, alice))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_transactions_payment_method_by_type");
    }

    @Test
    void databaseRejectsUnknownPaymentMethod() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO transactions (user_id, amount, type, payment_method, occurred_on, created_at)
                VALUES (?, 10, 'EXPENSE', 'CHEQUE', DATE '2026-10-06', now())
                """, alice))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_transactions_payment_method");
    }

    @Test
    void findInPeriodRespectsMonthBoundaries() {
        saveExpense(alice, null, "30/09", LocalDate.of(2026, 9, 30));
        saveExpense(alice, null, "01/10", LocalDate.of(2026, 10, 1));
        saveExpense(alice, null, "31/10", LocalDate.of(2026, 10, 31));
        saveExpense(alice, null, "01/11", LocalDate.of(2026, 11, 1));
        flushAndClear();

        List<Transaction> october = transactions.findInPeriod(alice, OCT_1, NOV_1, null);

        // Ordenado da mais recente para a mais antiga
        assertThat(october).extracting(Transaction::getDescription).containsExactly("31/10", "01/10");
    }

    @Test
    void findInPeriodReturnsOnlyTransactionsOfTheUser() {
        saveExpense(alice, null, "da Alice", DAY);
        saveExpense(bob, null, "do Bob", DAY);
        flushAndClear();

        assertThat(transactions.findInPeriod(alice, OCT_1, NOV_1, null))
                .extracting(Transaction::getDescription).containsExactly("da Alice");
    }

    @Test
    void findInPeriodFiltersByCategoryAndIncludesUncategorizedWhenNoFilter() {
        Category groceries = categories.save(new Category(alice, "Mercado", TransactionType.EXPENSE));
        Category transport = categories.save(new Category(alice, "Transporte", TransactionType.EXPENSE));
        saveExpense(alice, groceries, "mercado", DAY);
        saveExpense(alice, transport, "uber", DAY);
        saveExpense(alice, null, "sem categoria", DAY);
        flushAndClear();

        assertThat(transactions.findInPeriod(alice, OCT_1, NOV_1, transport.getId()))
                .extracting(Transaction::getDescription).containsExactly("uber");
        assertThat(transactions.findInPeriod(alice, OCT_1, NOV_1, null))
                .extracting(Transaction::getDescription)
                .containsExactlyInAnyOrder("mercado", "uber", "sem categoria");
    }

    @Test
    void findInPeriodLoadsCategoriesInASingleQuery() {
        for (int i = 1; i <= 5; i++) {
            Category category = categories.save(new Category(alice, "Categoria " + i, TransactionType.EXPENSE));
            saveExpense(alice, category, "gasto " + i, DAY);
        }
        flushAndClear();

        Statistics stats = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        List<Transaction> result = transactions.findInPeriod(alice, OCT_1, NOV_1, null);
        result.forEach(tx -> tx.getCategory().getName()); // acessa cada categoria

        // Sem o JOIN FETCH seriam 6 consultas: 1 para as transações + 1 por categoria (N+1).
        assertThat(result).hasSize(5);
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
    }

    private Long saveExpense(Long userId, Category category, String description, LocalDate day) {
        return transactions.save(new Transaction(
                userId, category, new BigDecimal("10.00"), TransactionType.EXPENSE, PaymentMethod.PIX, description, day)).getId();
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
