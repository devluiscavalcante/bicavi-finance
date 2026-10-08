package com.bicavi.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    // Só o hash. A senha em texto puro nunca é guardada nem chega a esta classe.
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {
    }

    public User(String email, String passwordHash, String name) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        // Exceção consciente à regra do ClockConfig ("use o Clock, não Instant.now()"):
        // createdAt é só registro de auditoria, nenhuma regra ou teste depende dele.
        // Usar o Clock exigiria recebê-lo no construtor da entidade, em todo lugar
        // que a cria, sem ganho prático. Se um dia uma regra depender deste horário,
        // aí sim ele deve vir do Clock.
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }
}
