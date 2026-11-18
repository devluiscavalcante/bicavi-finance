package com.bicavi.card;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

// Cartão de crédito do usuário (ex.: "Banco do Brasil", "Nubank").
// Dia de fechamento/vencimento da fatura é evolução futura.
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

    // ----- Dados do desenho do cartão (V12) -----
    // Ficam null nos cartões cadastrados antes da V12, que só tinham o nome.
    // Em cartão novo ou editado são todos obrigatórios (ver apply), e o banco
    // garante "todos ou nenhum" (ck_cards_details_all_or_none).

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CardBrand brand;

    @Column(name = "holder_name", length = 40)
    private String holderName;

    // Só os 4 últimos dígitos. O número completo e o código de segurança nunca
    // são pedidos nem guardados. String e não número: "0042" perderia os zeros.
    @Column(name = "last_four", length = 4)
    private String lastFour;

    // Validade como no cartão (MM/AA): mês 1 a 12 e ano com 4 dígitos.
    @Column(name = "expiry_month")
    private Integer expiryMonth;

    @Column(name = "expiry_year")
    private Integer expiryYear;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CardColor color;

    // Exigido pelo JPA para criar o objeto ao ler do banco. Não usar no código.
    protected Card() {
    }

    public Card(Long userId, String name, CardBrand brand, String holderName, String lastFour,
                int expiryMonth, int expiryYear, CardColor color) {
        this.userId = userId;
        apply(name, brand, holderName, lastFour, expiryMonth, expiryYear, color);
    }

    // Substitui todos os dados editáveis. É assim que um cartão antigo (só com
    // o nome) passa a ter os demais dados.
    public void update(String name, CardBrand brand, String holderName, String lastFour,
                       int expiryMonth, int expiryYear, CardColor color) {
        apply(name, brand, holderName, lastFour, expiryMonth, expiryYear, color);
    }

    // private: não pode ser sobrescrito, então é seguro chamar no construtor.
    private void apply(String name, CardBrand brand, String holderName, String lastFour,
                       int expiryMonth, int expiryYear, CardColor color) {
        // Defesa extra: quem valida o que o cliente manda é o CardRequest (@Valid).
        // Se algum destes chegar null aqui, é BUG nosso (500), não erro do cliente.
        // Confere tudo ANTES de alterar: um erro não deixa o cartão pela metade.
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(brand, "brand");
        Objects.requireNonNull(holderName, "holderName");
        Objects.requireNonNull(lastFour, "lastFour");
        Objects.requireNonNull(color, "color");
        this.name = name;
        this.brand = brand;
        this.holderName = holderName;
        this.lastFour = lastFour;
        this.expiryMonth = expiryMonth;
        this.expiryYear = expiryYear;
        this.color = color;
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

    public CardBrand getBrand() {
        return brand;
    }

    public String getHolderName() {
        return holderName;
    }

    public String getLastFour() {
        return lastFour;
    }

    public Integer getExpiryMonth() {
        return expiryMonth;
    }

    public Integer getExpiryYear() {
        return expiryYear;
    }

    public CardColor getColor() {
        return color;
    }
}
