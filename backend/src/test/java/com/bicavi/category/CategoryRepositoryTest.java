package com.bicavi.category;

import com.bicavi.TestcontainersConfiguration;
import com.bicavi.transaction.TransactionType;
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

// @DataJpaTest sobe só a camada de persistência (JPA + Flyway) e desfaz
// (rollback) cada teste ao final. O banco é um PostgreSQL descartável
// (Testcontainers); replace = NONE impede a troca por um banco em memória.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class CategoryRepositoryTest {

    @Autowired
    private CategoryRepository repository;

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
    void savesAndFindsCategoryOfOwner() {
        Category saved = repository.save(new Category(alice, "Mercado", TransactionType.EXPENSE));

        Category found = repository.findByIdAndUserId(saved.getId(), alice).orElseThrow();
        assertThat(found.getName()).isEqualTo("Mercado");
        assertThat(found.getType()).isEqualTo(TransactionType.EXPENSE);
    }

    @Test
    void doesNotFindCategoryOfAnotherUser() {
        Category alices = repository.save(new Category(alice, "Mercado", TransactionType.EXPENSE));

        assertThat(repository.findByIdAndUserId(alices.getId(), bob)).isEmpty();
    }

    @Test
    void listsOnlyCategoriesOfTheUserSortedByName() {
        repository.save(new Category(alice, "Transporte", TransactionType.EXPENSE));
        repository.save(new Category(alice, "Mercado", TransactionType.EXPENSE));
        repository.save(new Category(bob, "Lazer", TransactionType.EXPENSE));

        assertThat(repository.findByUserIdOrderByNameAsc(alice))
                .extracting(Category::getName).containsExactly("Mercado", "Transporte");
    }

    @Test
    void storesTypeAsTextInDatabase() {
        Category saved = repository.saveAndFlush(new Category(alice, "Salário", TransactionType.INCOME));

        String typeInDb = jdbc.queryForObject(
                "SELECT type FROM categories WHERE id = ?", String.class, saved.getId());
        assertThat(typeInDb).isEqualTo("INCOME");
    }

    @Test
    void rejectsDuplicateNameForSameUser() {
        repository.saveAndFlush(new Category(alice, "Transporte", TransactionType.EXPENSE));

        assertThatThrownBy(() -> repository.saveAndFlush(new Category(alice, "Transporte", TransactionType.EXPENSE)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsSameNameForDifferentUsers() {
        repository.saveAndFlush(new Category(alice, "Mercado", TransactionType.EXPENSE));
        repository.saveAndFlush(new Category(bob, "Mercado", TransactionType.EXPENSE));

        assertThat(repository.existsByUserIdAndName(alice, "Mercado")).isTrue();
        assertThat(repository.existsByUserIdAndName(bob, "Mercado")).isTrue();
    }

    @Test
    void rejectsInvalidTypeDirectlyInDatabase() {
        // user_id válido: queremos que o banco recuse por causa do CHECK do tipo,
        // e não por falta de dono (o teste passaria pelo motivo errado).
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO categories (user_id, name, type) VALUES (?, 'X', 'OUTRO')", alice))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_categories_type");
    }
}
