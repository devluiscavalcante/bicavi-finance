package com.bicavi.transaction;

// Alcance de uma edição/exclusão de parcela (?scope=THIS ou ?scope=FOLLOWING).
// Em transação que não é parcela, os dois dão no mesmo.
public enum EditScope {
    // Só a transação indicada.
    THIS,
    // Ela e as parcelas seguintes da mesma compra (as anteriores ficam como estão).
    FOLLOWING
}
