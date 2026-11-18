package com.bicavi.card;

import com.bicavi.TestcontainersConfiguration;
import com.bicavi.user.User;
import com.bicavi.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Banco real (Testcontainers) com as migrations aplicadas: testa o repository
// e as regras que a V8 e a V12 colocaram no próprio banco.
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

    @Autowired
    private EntityManager entityManager;

    private Long alice;
    private Long bob;

    @BeforeEach
    void createUsers() {
        alice = users.save(new User("alice@example.com", "$2a$10$hash", "Alice")).getId();
        bob = users.save(new User("bob@example.com", "$2a$10$hash", "Bob")).getId();
    }

    @Test
    void findsCardOnlyForItsOwner() {
        Card saved = repository.save(TestCards.card(alice, "Banco do Brasil"));

        assertThat(repository.findByIdAndUserId(saved.getId(), alice)).isPresent();
        assertThat(repository.findByIdAndUserId(saved.getId(), bob)).isEmpty();
    }

    @Test
    void listsOnlyCardsOfTheUserSortedByName() {
        repository.save(TestCards.card(alice, "Nubank"));
        repository.save(TestCards.card(alice, "Banco do Brasil"));
        repository.save(TestCards.card(bob, "Itaú"));

        assertThat(repository.findByUserIdOrderByNameAsc(alice))
                .extracting(Card::getName).containsExactly("Banco do Brasil", "Nubank");
    }

    @Test
    void rejectsSameNameWithDifferentCaseForSameUser() {
        repository.saveAndFlush(TestCards.card(alice, "Nubank"));

        assertThatThrownBy(() -> repository.saveAndFlush(TestCards.card(alice, "NUBANK")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_cards_user_lower_name");
    }

    @Test
    void allowsSameNameForDifferentUsers() {
        repository.saveAndFlush(TestCards.card(alice, "Nubank"));
        repository.saveAndFlush(TestCards.card(bob, "Nubank"));

        assertThat(repository.existsByUserIdAndNameIgnoreCase(bob, "nubank")).isTrue();
    }

    @Test
    void databaseAcceptsCardOnCreditExpense() {
        Long card = repository.saveAndFlush(TestCards.card(alice, "Nubank")).getId();

        insertExpense("CREDITO", card);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE card_id = ?", Long.class, card))
                .isEqualTo(1);
    }

    @Test
    void databaseRejectsCardOnNonCreditExpense() {
        Long card = repository.saveAndFlush(TestCards.card(alice, "Nubank")).getId();

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
        Card card = repository.saveAndFlush(TestCards.card(alice, "Nubank"));
        insertExpense("CREDITO", card.getId());

        repository.delete(card);
        repository.flush();

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE user_id = ? AND card_id IS NULL", Long.class, alice))
                .isEqualTo(1);
    }

    // "before" é exclusivo: compra no dia 01/10 não conta como "antes de 01/10".
    @Test
    void hasTransactionsBeforeLooksOnlyAtDatesBeforeTheLimit() {
        Long card = repository.saveAndFlush(TestCards.card(alice, "Nubank")).getId();
        insertExpense("CREDITO", card, LocalDate.of(2026, 10, 1));

        assertThat(repository.hasTransactionsBefore(alice, card, LocalDate.of(2026, 10, 1))).isFalse();
        assertThat(repository.hasTransactionsBefore(alice, card, LocalDate.of(2026, 10, 2))).isTrue();
    }

    @Test
    void hasTransactionsBeforeIgnoresOtherCards() {
        Long nubank = repository.saveAndFlush(TestCards.card(alice, "Nubank")).getId();
        Long itau = repository.saveAndFlush(TestCards.card(alice, "Itaú")).getId();
        insertExpense("CREDITO", nubank, LocalDate.of(2026, 9, 10));

        assertThat(repository.hasTransactionsBefore(alice, itau, LocalDate.of(2026, 10, 1))).isFalse();
    }

    // ----- Dados do desenho do cartão (V12) -----

    @Test
    void savesAndReadsBrandHolderLastFourExpiryAndColor() {
        Long id = repository.save(new Card(alice, "Nubank", CardBrand.MASTERCARD, "Maria Silva dos Santos",
                "0042", 3, 2031, CardColor.ROXO)).getId();
        flushAndClear();

        Card found = repository.findByIdAndUserId(id, alice).orElseThrow();

        assertThat(found.getBrand()).isEqualTo(CardBrand.MASTERCARD);
        assertThat(found.getHolderName()).isEqualTo("Maria Silva dos Santos");
        // Os zeros à esquerda continuam lá: a coluna é texto.
        assertThat(found.getLastFour()).isEqualTo("0042");
        assertThat(found.getExpiryMonth()).isEqualTo(3);
        assertThat(found.getExpiryYear()).isEqualTo(2031);
        assertThat(found.getColor()).isEqualTo(CardColor.ROXO);
    }

    // Cartão de antes da V12: a linha só tem o nome. Ele continua carregando
    // (com os demais dados null) e é completado de uma vez ao ser editado.
    @Test
    void oldCardWithOnlyTheNameLoadsAndIsCompletedByUpdate() {
        Long id = jdbc.queryForObject(
                "INSERT INTO cards (user_id, name) VALUES (?, 'Cartão antigo') RETURNING id", Long.class, alice);

        Card old = repository.findByIdAndUserId(id, alice).orElseThrow();
        assertThat(old.getName()).isEqualTo("Cartão antigo");
        assertThat(old.getBrand()).isNull();
        assertThat(old.getHolderName()).isNull();
        assertThat(old.getLastFour()).isNull();
        assertThat(old.getExpiryMonth()).isNull();
        assertThat(old.getExpiryYear()).isNull();
        assertThat(old.getColor()).isNull();

        old.update("Cartão antigo", CardBrand.ELO, "Alice Souza", "7788", 12, 2030, CardColor.AURORA);
        flushAndClear();

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT brand, holder_name, last_four, expiry_month, expiry_year, color FROM cards WHERE id = ?", id);
        assertThat(row)
                .containsEntry("brand", "ELO")
                .containsEntry("holder_name", "Alice Souza")
                .containsEntry("last_four", "7788")
                .containsEntry("expiry_month", 12)
                .containsEntry("expiry_year", 2030)
                .containsEntry("color", "AURORA");
    }

    // O banco recusa o cartão "pela metade": os dados entram todos ou nenhum.
    @Test
    void databaseRejectsNewRowWithOnlyPartOfTheDetails() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO cards (user_id, name, brand, last_four) VALUES (?, 'Pela metade', 'VISA', '1234')
                """, alice))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_cards_details_all_or_none");
    }

    // Cada linha estraga UM dado de um cartão válido, direto no banco (sem passar
    // pela API), e diz qual CHECK da V12 tem de recusar. O trecho de SQL é texto
    // fixo deste teste, por isso pode ser concatenado.
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
            last_four = '12a4'   | ck_cards_last_four
            last_four = '123'    | ck_cards_last_four
            last_four = '12 4'   | ck_cards_last_four
            expiry_month = 0     | ck_cards_expiry_month
            expiry_month = 13    | ck_cards_expiry_month
            expiry_year = 1999   | ck_cards_expiry_year
            expiry_year = 2100   | ck_cards_expiry_year
            brand = 'DINERS'     | ck_cards_brand
            color = 'VERDE'      | ck_cards_color
            holder_name = NULL   | ck_cards_details_all_or_none
            color = NULL         | ck_cards_details_all_or_none
            """)
    void databaseRejectsInvalidDetails(String change, String constraint) {
        Long id = repository.saveAndFlush(TestCards.card(alice, "Nubank")).getId();

        assertThatThrownBy(() -> jdbc.update("UPDATE cards SET " + change + " WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraint);
    }

    // Grava o que está pendente e esvazia o cache do Hibernate: a próxima busca
    // lê do banco de verdade, e não o objeto que acabou de ser salvo.
    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private void insertExpense(String paymentMethod, Long cardId) {
        insertExpense(paymentMethod, cardId, LocalDate.now());
    }

    private void insertExpense(String paymentMethod, Long cardId, LocalDate occurredOn) {
        jdbc.update("""
                INSERT INTO transactions (user_id, amount, type, payment_method, card_id, occurred_on, created_at)
                VALUES (?, 10.00, 'EXPENSE', ?, ?, ?, NOW())
                """, alice, paymentMethod, cardId, occurredOn);
    }
}
