package com.bicavi.card;

import com.bicavi.TestcontainersConfiguration;
import com.bicavi.user.User;
import com.bicavi.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Banco real (Testcontainers) com a V8 aplicada: testa o repository e as
// regras que a migration colocou no próprio banco.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class CardRepositoryTest {

    @Autowired
    private CardRepository repository;

    @Autowired
    private UserRepository users;

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
    void findsCardOnlyForItsOwner() {
        Card saved = repository.save(new Card(alice, "Banco do Brasil"));

        assertThat(repository.findByIdAndUserId(saved.getId(), alice)).isPresent();
        assertThat(repository.findByIdAndUserId(saved.getId(), bob)).isEmpty();
    }

    @Test
    void listsOnlyCardsOfTheUserSortedByName() {
        repository.save(new Card(alice, "Nubank"));
        repository.save(new Card(alice, "Banco do Brasil"));
        repository.save(new Card(bob, "Itaú"));

        assertThat(repository.findByUserIdOrderByNameAsc(alice))
                .extracting(Card::getName).containsExactly("Banco do Brasil", "Nubank");
    }

    @Test
    void rejectsSameNameWithDifferentCaseForSameUser() {
        repository.saveAndFlush(new Card(alice, "Nubank"));

        assertThatThrownBy(() -> repository.saveAndFlush(new Card(alice, "NUBANK")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_cards_user_lower_name");
    }

    @Test
    void allowsSameNameForDifferentUsers() {
        repository.saveAndFlush(new Card(alice, "Nubank"));
        repository.saveAndFlush(new Card(bob, "Nubank"));

        assertThat(repository.existsByUserIdAndNameIgnoreCase(bob, "nubank")).isTrue();
    }

    @Test
    void databaseAcceptsCardOnCreditExpense() {
        Long card = repository.saveAndFlush(new Card(alice, "Nubank")).getId();

        insertExpense("CREDITO", card);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE card_id = ?", Long.class, card))
                .isEqualTo(1);
    }

    @Test
    void databaseRejectsCardOnNonCreditExpense() {
        Long card = repository.saveAndFlush(new Card(alice, "Nubank")).getId();

        assertThatThrownBy(() -> insertExpense("PIX", card))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_transactions_card_only_credit");
    }

    // Crédito sem cartão continua aceito NO BANCO: são as despesas antigas,
    // de antes da V8. Quem exige o cartão nas novas é o service.
    @Test
    void databaseStillAcceptsCreditExpenseWithoutCard() {
        insertExpense("CREDITO", null);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE user_id = ? AND card_id IS NULL", Long.class, alice))
                .isEqualTo(1);
    }

    @Test
    void deletingCardKeepsItsExpensesWithoutCard() {
        Card card = repository.saveAndFlush(new Card(alice, "Nubank"));
        insertExpense("CREDITO", card.getId());

        repository.delete(card);
        repository.flush();

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE user_id = ? AND card_id IS NULL", Long.class, alice))
                .isEqualTo(1);
    }

    private void insertExpense(String paymentMethod, Long cardId) {
        jdbc.update("""
                INSERT INTO transactions (user_id, amount, type, payment_method, card_id, occurred_on, created_at)
                VALUES (?, 10.00, 'EXPENSE', ?, ?, CURRENT_DATE, NOW())
                """, alice, paymentMethod, cardId);
    }
}
