package com.bicavi.transaction;

import com.bicavi.card.Card;
import com.bicavi.category.Category;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// Monta transações para os testes ("test data builder").
//
// Em vez de  new Transaction(USER, null, new BigDecimal("8.00"), EXPENSE, CREDITO, nubank, null, DAY)
// escreve-se TestTransactions.expense("8.00").credit(nubank).build()
//
// Cada teste diz só o que importa para ele; o resto vem de valores padrão
// válidos. E um campo novo na Transaction muda só esta classe, não dezenas
// de testes (foi o que aconteceu ao adicionar o cartão).
//
// Passa sempre pelo construtor/fábrica real: as regras da entidade continuam
// valendo, e dá para montar casos inválidos de propósito (ex.: payment(null)).
public final class TestTransactions {

    // Os mesmos valores que os testes já usavam: usuário 1 e "hoje" = 06/10/2026.
    public static final Long DEFAULT_USER = 1L;
    public static final LocalDate DEFAULT_DATE = LocalDate.of(2026, 10, 6);

    private TestTransactions() {
    }

    // Despesa no Pix, sem categoria, sem descrição.
    public static Builder expense(String amount) {
        return expense(new BigDecimal(amount));
    }

    public static Builder expense(BigDecimal amount) {
        return new Builder(TransactionType.EXPENSE, amount).payment(PaymentMethod.PIX);
    }

    // Receita: sem forma de pagamento.
    public static Builder income(String amount) {
        return new Builder(TransactionType.INCOME, new BigDecimal(amount));
    }

    public static final class Builder {

        private final TransactionType type;
        private final BigDecimal amount;
        private Long userId = DEFAULT_USER;
        private Category category;
        private PaymentMethod paymentMethod;
        private Card card;
        private String description;
        private LocalDate occurredOn = DEFAULT_DATE;
        private UUID installmentGroup;
        private int installmentNumber;
        private int installmentCount;

        private Builder(TransactionType type, BigDecimal amount) {
            this.type = type;
            this.amount = amount;
        }

        public Builder user(Long userId) {
            this.userId = userId;
            return this;
        }

        public Builder category(Category category) {
            this.category = category;
            return this;
        }

        public Builder payment(PaymentMethod paymentMethod) {
            this.paymentMethod = paymentMethod;
            return this;
        }

        // Compra no crédito com este cartão (as duas coisas andam juntas).
        public Builder credit(Card card) {
            this.paymentMethod = PaymentMethod.CREDITO;
            this.card = card;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder on(LocalDate occurredOn) {
            this.occurredOn = occurredOn;
            return this;
        }

        // Parcela "number" de "count" da compra "group".
        public Builder installment(UUID group, int number, int count) {
            this.installmentGroup = group;
            this.installmentNumber = number;
            this.installmentCount = count;
            return this;
        }

        public Transaction build() {
            if (installmentGroup == null) {
                return new Transaction(userId, category, amount, type, paymentMethod, card, description, occurredOn);
            }
            if (type != TransactionType.EXPENSE) {
                throw new IllegalStateException("Parcela é sempre despesa: use expense(...)");
            }
            return Transaction.installment(userId, category, amount, paymentMethod, card, description, occurredOn,
                    installmentGroup, installmentNumber, installmentCount);
        }
    }
}
