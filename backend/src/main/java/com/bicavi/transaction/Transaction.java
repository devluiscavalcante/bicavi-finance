package com.bicavi.transaction;

import com.bicavi.category.Category;
import com.bicavi.common.BusinessRuleException;
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

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

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

    // null em receitas. A regra "despesa exige, receita não tem" está em validate()
    // e também num CHECK do banco (V5).
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 10)
    private PaymentMethod paymentMethod;

    @Column(length = 255)
    private String description;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Transaction() {
    }

    public Transaction(Long userId, Category category, BigDecimal amount, TransactionType type,
                       PaymentMethod paymentMethod, String description, LocalDate occurredOn) {
        this.userId = userId;
        apply(category, amount, type, paymentMethod, description, occurredOn);
        this.createdAt = Instant.now();
    }

    // Substitui todos os dados editáveis, aplicando as mesmas regras da criação.
    public void update(Category category, BigDecimal amount, TransactionType type,
                       PaymentMethod paymentMethod, String description, LocalDate occurredOn) {
        apply(category, amount, type, paymentMethod, description, occurredOn);
    }

    // private: não pode ser sobrescrito, então é seguro chamar no construtor.
    private void apply(Category category, BigDecimal amount, TransactionType type,
                       PaymentMethod paymentMethod, String description, LocalDate occurredOn) {
        validate(category, amount, type, paymentMethod, occurredOn);
        // Defesa extra: o service só deveria passar categorias do próprio usuário.
        // Se isto disparar, é BUG nosso (500), não erro do cliente (400).
        if (category != null && !category.getUserId().equals(userId)) {
            throw new IllegalStateException("Categoria de outro usuário associada à transação");
        }
        this.category = category;
        this.amount = amount;
        this.type = type;
        this.paymentMethod = paymentMethod;
        this.description = description;
        this.occurredOn = occurredOn;
    }

    private static void validate(Category category, BigDecimal amount, TransactionType type,
                                 PaymentMethod paymentMethod, LocalDate occurredOn) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException("O valor deve ser maior que zero");
        }
        // O banco arredondaria 10.555 para 10.56 sem avisar. Melhor recusar.
        if (amount.scale() > 2) {
            throw new BusinessRuleException("O valor deve ter no máximo 2 casas decimais");
        }
        if (type == null) {
            throw new BusinessRuleException("O tipo é obrigatório");
        }
        if (type == TransactionType.EXPENSE && paymentMethod == null) {
            throw new BusinessRuleException("A forma de pagamento é obrigatória para despesas");
        }
        if (type == TransactionType.INCOME && paymentMethod != null) {
            throw new BusinessRuleException("Receitas não têm forma de pagamento");
        }
        if (occurredOn == null) {
            throw new BusinessRuleException("A data é obrigatória");
        }
        if (category != null && category.getType() != type) {
            throw new BusinessRuleException("A categoria '" + category.getName()
                    + "' só aceita transações do tipo " + category.getType());
        }
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
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

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
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
