package com.bicavi.transaction;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

// Compra parcelada: sempre uma DESPESA, então não há campo "type".
// totalAmount é o valor TOTAL da compra; o backend divide entre as parcelas.
// firstDate é a data da 1ª parcela (no crédito, a da fatura em que ela cai);
// as demais vêm nos meses seguintes.
public record InstallmentRequest(
        @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal totalAmount,
        @NotNull @Min(2) @Max(24) Integer installments,
        PaymentMethod paymentMethod,  // obrigatório (é despesa); validado na entidade
        Long categoryId,
        // Mesma descrição em todas as parcelas; o "(2/3)" vem de installmentNumber/Count.
        @Size(max = 255) String description,
        @NotNull LocalDate firstDate
) {
}
