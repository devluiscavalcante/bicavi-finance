package com.bicavi.card;

// Bandeira do cartão. É o usuário que escolhe: o app só conhece os 4 últimos
// dígitos, e a bandeira só poderia ser deduzida pelos PRIMEIROS.
// Os mesmos valores estão no CHECK ck_cards_brand (V12): bandeira nova = enum + migration.
public enum CardBrand {
    VISA,
    MASTERCARD,
    ELO,
    AMEX,
    HIPERCARD,
    OUTRA
}
