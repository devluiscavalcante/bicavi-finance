package com.bicavi.category;

import com.bicavi.TestcontainersConfiguration;
import com.bicavi.transaction.TransactionType;
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
    private JdbcTemplate jdbc;

    @Test
    void savesAndFindsCategory() {
        Category saved = repository.save(new Category("Mercado", TransactionType.EXPENSE));

        assertThat(saved.getId()).isNotNull();
        Category found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getName()).isEqualTo("Mercado");
        assertThat(found.getType()).isEqualTo(TransactionType.EXPENSE);
    }

    @Test
    void storesTypeAsTextInDatabase() {
        Category saved = repository.saveAndFlush(new Category("Salário", TransactionType.INCOME));

        String typeInDb = jdbc.queryForObject(
                "SELECT type FROM categories WHERE id = ?", String.class, saved.getId());
        assertThat(typeInDb).isEqualTo("INCOME");
    }

    @Test
    void rejectsDuplicateName() {
        repository.saveAndFlush(new Category("Transporte", TransactionType.EXPENSE));

        assertThatThrownBy(() -> repository.saveAndFlush(new Category("Transporte", TransactionType.EXPENSE)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsInvalidTypeDirectlyInDatabase() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO categories (name, type) VALUES ('X', 'OUTRO')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
