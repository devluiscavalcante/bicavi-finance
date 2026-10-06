package com.bicavi.transaction;

import com.bicavi.TestcontainersConfiguration;
import com.bicavi.category.Category;
import com.bicavi.category.CategoryRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

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

    // flush: envia ao banco os comandos SQL pendentes.
    // clear: esvazia o cache do JPA, forçando a próxima leitura a ir ao banco.
    // Sem o clear, o JPA devolveria o objeto que está em memória, e não
    // veríamos o efeito do ON DELETE SET NULL, que acontece só no banco.
    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
