package com.bicavi.card;

public record CardResponse(Long id, String name) {

    public static CardResponse from(Card card) {
        return new CardResponse(card.getId(), card.getName());
    }
}
