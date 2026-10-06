package com.bicavi.user;

import com.bicavi.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class UserRepositoryTest {

    @Autowired
    private UserRepository users;

    @Test
    void findsUserByEmail() {
        users.save(new User("luis@example.com", "$2a$10$hash", "Luis"));

        assertThat(users.existsByEmail("luis@example.com")).isTrue();
        assertThat(users.findByEmail("luis@example.com")).get()
                .extracting(User::getName).isEqualTo("Luis");
        assertThat(users.findByEmail("outro@example.com")).isEmpty();
    }

    @Test
    void databaseRejectsDuplicateEmail() {
        users.saveAndFlush(new User("luis@example.com", "$2a$10$hash", "Luis"));

        assertThatThrownBy(() -> users.saveAndFlush(new User("luis@example.com", "$2a$10$outro", "Outro")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
