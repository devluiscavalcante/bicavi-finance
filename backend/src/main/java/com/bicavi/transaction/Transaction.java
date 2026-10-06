package com.bicavi.transaction;

import com.bicavi.category.Category;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

// Transação FINANCEIRA (receita/despesa). Não confundir com transação de
// banco de dados (@Transactional), que é outro conceito.
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Muitas transações -> uma categoria. LAZY: a categoria só é buscada
    // no banco se alguém chamar getCategory().
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionType type;

    @Column(length = 255)
    private String description;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Transaction() {
    }

    public Transaction(Category category, BigDecimal amount, TransactionType type,
                       String description, LocalDate occurredOn) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("O valor deve ser maior que zero");
        }
        // O banco arredondaria 10.555 para 10.56 sem avisar. Melhor recusar.
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("O valor deve ter no máximo 2 casas decimais");
        }
        if (type == null) {
            throw new IllegalArgumentException("O tipo é obrigatório");
        }
        if (occurredOn == null) {
            throw new IllegalArgumentException("A data é obrigatória");
        }
        if (category != null && category.getType() != type) {
            throw new IllegalArgumentException("A categoria '" + category.getName()
                    + "' só aceita transações do tipo " + category.getType());
        }

        this.category = category;
        this.amount = amount;
        this.type = type;
        this.description = description;
        this.occurredOn = occurredOn;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Category getCategory() {
        return category;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransactionType getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getOccurredOn() {
        return occurredOn;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
