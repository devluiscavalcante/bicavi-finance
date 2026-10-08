package com.bicavi.transaction;

import com.bicavi.TestcontainersConfiguration;
import com.bicavi.card.Card;
import com.bicavi.card.CardRepository;
import com.bicavi.category.Category;
import com.bicavi.category.CategoryRepository;
import com.bicavi.common.BusinessRuleException;
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
import java.util.UUID;

import static com.bicavi.transaction.TestTransactions.expense;
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
    private CardRepository cards;

    @Autowired
    private UserRepository users;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    private Long alice;
    private Long bob;
    private Card aliceCard;  // compras no crédito exigem cartão

    @BeforeEach
    void createUsers() {
        alice = users.save(new User("alice@example.com", "$2a$10$hash", "Alice")).getId();
        bob = users.save(new User("bob@example.com", "$2a$10$hash", "Bob")).getId();
        aliceCard = cards.save(new Card(alice, "Nubank"));
    }

    @Test
    void savesAndReadsTransactionWithCategory() {
        Category groceries = categories.save(new Category(alice, "Mercado", TransactionType.EXPENSE));
        Long id = transactions.save(
                expense("35.90").user(alice).category(groceries).description("Feira").build()).getId();
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
        Long id = transactions.save(expense(amount).user(alice).build()).getId();
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
    void savesAndReadsPaymentMethodAndCard() {
        Long id = transactions.save(expense("30.00").user(alice).credit(aliceCard).description("Caderno").build()).getId();
        flushAndClear();

        // Grava o NOME do enum (EnumType.STRING), não a posição (0, 1, 2...).
        String inDb = jdbc.queryForObject("SELECT payment_method FROM transactions WHERE id = ?", String.class, id);
        assertThat(inDb).isEqualTo("CREDITO");
        Transaction found = transactions.findByIdAndUserId(id, alice).orElseThrow();
        assertThat(found.getPaymentMethod()).isEqualTo(PaymentMethod.CREDITO);
        assertThat(found.getCard().getName()).isEqualTo("Nubank");
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
    void findInPeriodLoadsCategoriesAndCardsInASingleQuery() {
        for (int i = 1; i <= 5; i++) {
            Category category = categories.save(new Category(alice, "Categoria " + i, TransactionType.EXPENSE));
            Card card = cards.save(new Card(alice, "Cartão " + i));
            transactions.save(expense("10.00").user(alice).category(category).credit(card)
                    .description("gasto " + i).build());
        }
        flushAndClear();

        Statistics stats = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        List<Transaction> result = transactions.findInPeriod(alice, OCT_1, NOV_1, null);
        // Acessa cada categoria e cada cartão, como o TransactionResponse faz.
        result.forEach(tx -> {
            tx.getCategory().getName();
            tx.getCard().getName();
        });

        // Sem os JOIN FETCH seriam 11 consultas: 1 para as transações
        // + 1 por categoria + 1 por cartão (o problema N+1).
        assertThat(result).hasSize(5);
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
    }

    // Despesa no crédito de antes da V8: está no banco sem cartão. Ela continua
    // sendo lida normalmente, mas para salvar uma edição é preciso informar o cartão.
    @Test
    void legacyCreditExpenseWithoutCardLoadsButRequiresCardWhenEdited() {
        Long id = jdbc.queryForObject("""
                INSERT INTO transactions (user_id, amount, type, payment_method, occurred_on, created_at)
                VALUES (?, 30.00, 'EXPENSE', 'CREDITO', ?, now())
                RETURNING id
                """, Long.class, alice, DAY);

        Transaction legacy = transactions.findByIdAndUserId(id, alice).orElseThrow();
        assertThat(legacy.getCard()).isNull();

        assertThatThrownBy(() -> legacy.update(null, legacy.getAmount(), TransactionType.EXPENSE,
                PaymentMethod.CREDITO, null, null, DAY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Informe o cartão da compra no crédito");

        legacy.update(null, legacy.getAmount(), TransactionType.EXPENSE, PaymentMethod.CREDITO, aliceCard, null, DAY);
        flushAndClear();
        assertThat(transactions.findByIdAndUserId(id, alice).orElseThrow().getCard().getName()).isEqualTo("Nubank");
    }

    @Test
    void findsOnlyTheFollowingInstallmentsOfTheSameGroupAndUser() {
        UUID ps5 = UUID.randomUUID();
        UUID tv = UUID.randomUUID();
        for (int n = 1; n <= 3; n++) {
            transactions.save(expense("100.00").user(alice).credit(aliceCard).description("PS5")
                    .on(DAY.plusMonths(n - 1)).installment(ps5, n, 3).build());
        }
        transactions.save(expense("50.00").user(alice).credit(aliceCard).description("TV")
                .on(DAY.plusMonths(1)).installment(tv, 2, 2).build());
        flushAndClear();

        assertThat(transactions.findFollowingInstallments(alice, ps5, 1))
                .extracting(Transaction::getInstallmentNumber).containsExactly(2, 3);
        assertThat(transactions.findFollowingInstallments(alice, ps5, 3)).isEmpty();
        // Mesmo conhecendo o grupo, outro usuário não enxerga as parcelas.
        assertThat(transactions.findFollowingInstallments(bob, ps5, 1)).isEmpty();
    }

    @Test
    void savesInstallmentColumnsAsUuidAndIntegers() {
        UUID group = UUID.randomUUID();
        Long id = transactions.save(expense("100.00").user(alice).credit(aliceCard).description("PS5")
                .installment(group, 2, 3).build()).getId();
        flushAndClear();

        Transaction found = transactions.findByIdAndUserId(id, alice).orElseThrow();
        assertThat(found.getInstallmentGroup()).isEqualTo(group);
        assertThat(found.getInstallmentNumber()).isEqualTo(2);
        assertThat(found.getInstallmentCount()).isEqualTo(3);
        assertThat(found.isInstallment()).isTrue();
    }

    // Um INSERT inválido por teste: no Postgres, depois de um erro a transação
    // fica "abortada" e os comandos seguintes nem são avaliados (erro 25P02).
    @Test
    void databaseRejectsInstallmentNumberAboveCount() {
        // Parcela 4 de 3: o CHECK da V7 recusa mesmo sem passar pela aplicação.
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO transactions (user_id, amount, type, payment_method, occurred_on, created_at,
                                          installment_group, installment_number, installment_count)
                VALUES (?, 10, 'EXPENSE', 'CREDITO', ?, now(), gen_random_uuid(), 4, 3)
                """, alice, DAY))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_transactions_installment");
    }

    @Test
    void databaseRejectsGroupWithoutNumberAndCount() {
        // Só o grupo, sem número e total: recusado (as três colunas andam juntas).
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO transactions (user_id, amount, type, payment_method, occurred_on, created_at, installment_group)
                VALUES (?, 10, 'EXPENSE', 'CREDITO', ?, now(), gen_random_uuid())
                """, alice, DAY))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_transactions_installment");
    }

    private Long saveExpense(Long userId, Category category, String description, LocalDate day) {
        return transactions.save(expense("10.00").user(userId).category(category).description(description).on(day).build()).getId();
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
