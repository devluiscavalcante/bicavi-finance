package com.bicavi.category;

import com.bicavi.TestcontainersConfiguration;
import com.bicavi.transaction.TransactionType;
import com.bicavi.user.User;
import com.bicavi.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// A V9 já rodou quando o banco de teste subiu, mas sem nenhum usuário (não fez nada).
// Aqui rodamos o MESMO SQL de novo, agora com usuários que já têm categorias,
// simulando o banco de produção no momento em que a migration for aplicada.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class DefaultCategoriesMigrationTest {

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createsOnlyTheMissingCategoriesIgnoringCase() throws IOException {
        Long luis = users.save(new User("luis@example.com", "$2a$10$hash", "Luis")).getId();
        categories.save(new Category(luis, "alimentação", TransactionType.EXPENSE));  // minúsculas
        categories.save(new Category(luis, "SAÚDE", TransactionType.EXPENSE));        // maiúsculas, com acento
        categories.save(new Category(luis, "Cartão BB", TransactionType.EXPENSE));    // não é da lista: fica
        categories.flush();

        runMigration();

        List<String> names = categories.findByUserIdOrderByNameAsc(luis).stream().map(Category::getName).toList();
        // 12 da lista (2 já existiam, com outra grafia) + "Cartão BB" = 13, sem duplicar nenhuma.
        assertThat(names).hasSize(13)
                .contains("alimentação", "SAÚDE", "Cartão BB", "Casa", "Cuidados pessoais", "Investimentos")
                .doesNotContain("Alimentação", "Saúde");
    }

    @Test
    void everyUserGetsTheirOwnCategoriesAndRunningAgainAddsNothing() throws IOException {
        Long alice = users.save(new User("alice@example.com", "$2a$10$hash", "Alice")).getId();
        Long bob = users.save(new User("bob@example.com", "$2a$10$hash", "Bob")).getId();

        runMigration();
        runMigration();

        assertThat(categories.findByUserIdOrderByNameAsc(alice)).hasSize(12)
                .allSatisfy(category -> assertThat(category.getType()).isEqualTo(TransactionType.EXPENSE));
        assertThat(categories.findByUserIdOrderByNameAsc(bob)).hasSize(12);
    }

    private void runMigration() throws IOException {
        String sql = new ClassPathResource("db/migration/V9__default_expense_categories.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        jdbc.execute(sql);
    }
}
