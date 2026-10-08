package com.bicavi.card;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Cartão de crédito do usuário (ex.: "Banco do Brasil", "Nubank").
// Só o nome por enquanto; dia de fechamento/vencimento da fatura é evolução futura.
@Entity
@Table(name = "cards")
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Dono do cartão: só o id, como em Category.
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String name;

    // Exigido pelo JPA para criar o objeto ao ler do banco. Não usar no código.
    protected Card() {
    }

    public Card(Long userId, String name) {
        this.userId = userId;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public void rename(String newName) {
        this.name = newName;
    }
}
