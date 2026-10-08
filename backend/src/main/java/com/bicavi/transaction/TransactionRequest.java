package com.bicavi.transaction;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

// Usado tanto no POST (criar) quanto no PUT (substituir todos os campos).
// Não tem id nem createdAt: o cliente não escolhe esses valores.
public record TransactionRequest(
        @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @NotNull TransactionType type,
        PaymentMethod paymentMethod,  // obrigatório em despesas; validado na entidade
        Long cardId,  // obrigatório no CREDITO, proibido nas demais; validado na entidade
        Long categoryId,  // opcional: null = sem categoria
        @Size(max = 255) String description,
        @NotNull LocalDate occurredOn
) {
}
