package com.bicavi.card;

// Cartão cadastrado antes da V12 só tem id e nome: os demais campos saem null,
// e o app pede para completar.
public record CardResponse(
        Long id,
        String name,
        CardBrand brand,
        String holderName,
        String lastFour,
        Integer expiryMonth,
        Integer expiryYear,
        CardColor color
) {

    public static CardResponse from(Card card) {
        return new CardResponse(card.getId(), card.getName(), card.getBrand(), card.getHolderName(),
                card.getLastFour(), card.getExpiryMonth(), card.getExpiryYear(), card.getColor());
    }
}
