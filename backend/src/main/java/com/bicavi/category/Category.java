package com.bicavi.category;

import com.bicavi.transaction.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Dono da categoria. Só o id (e não @ManyToOne User): nunca precisamos
    // dos dados do usuário a partir da categoria, apenas filtrar por ele.
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionType type;

    // Exigido pelo JPA para criar o objeto ao ler do banco. Não usar no código.
    protected Category() {
    }

    public Category(Long userId, String name, TransactionType type) {
        this.userId = userId;
        this.name = name;
        this.type = type;
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

    public TransactionType getType() {
        return type;
    }

    public void rename(String newName) {
        this.name = newName;
    }
}
