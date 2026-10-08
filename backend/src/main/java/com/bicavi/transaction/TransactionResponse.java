package com.bicavi.transaction;

import com.bicavi.card.Card;
import com.bicavi.category.Category;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionResponse(
        Long id,
        BigDecimal amount,
        TransactionType type,
        PaymentMethod paymentMethod,
        Long cardId,      // null se não for no crédito (ou crédito antigo, sem cartão)
        String cardName,
        Long categoryId,
        String categoryName,
        String description,
        LocalDate occurredOn,
        Integer installmentNumber,  // null se não for parcela
        Integer installmentCount    // null se não for parcela
) {

    public static TransactionResponse from(Transaction tx) {
        Card card = tx.getCard();
        Category category = tx.getCategory();
        return new TransactionResponse(
                tx.getId(),
                tx.getAmount(),
                tx.getType(),
                tx.getPaymentMethod(),
                card == null ? null : card.getId(),
                card == null ? null : card.getName(),
                category == null ? null : category.getId(),
                category == null ? null : category.getName(),
                tx.getDescription(),
                tx.getOccurredOn(),
                tx.getInstallmentNumber(),
                tx.getInstallmentCount());
    }
}
