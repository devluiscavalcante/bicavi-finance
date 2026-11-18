package com.bicavi.card;

// Monta cartões completos para os testes, na mesma ideia de TestTransactions:
// o teste diz só o que importa para ele (dono e nome) e o resto vem de valores
// padrão válidos. Um campo novo no Card muda só esta classe, não dezenas de testes.
public final class TestCards {

    private TestCards() {
    }

    // Visa preto da "Maria Silva", final 1234, validade 11/2029.
    public static Card card(Long userId, String name) {
        return new Card(userId, name, CardBrand.VISA, "Maria Silva", "1234", 11, 2029, CardColor.PRETO);
    }

    // Pedido válido com este nome: os mesmos dados de card().
    public static CardRequest request(String name) {
        return new CardRequest(name, CardBrand.VISA, "Maria Silva", "1234", 11, 2029, CardColor.PRETO);
    }
}
