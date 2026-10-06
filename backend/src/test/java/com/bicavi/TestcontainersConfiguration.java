package com.bicavi;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Testes que usam banco importam esta classe com @Import.
// O Spring sobe um container PostgreSQL novo (mesma versão do compose.yaml)
// e @ServiceConnection aponta o DataSource para ele automaticamente,
// ignorando a URL/usuário/senha do application.properties.
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:17");
    }
}
