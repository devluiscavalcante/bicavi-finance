package com.bicavi.transaction;

// Só para despesas. Compra no crédito conta no mês da compra (não da fatura).
public enum PaymentMethod {
    PIX,
    DINHEIRO,
    DEBITO,
    CREDITO,
    BOLETO
}
