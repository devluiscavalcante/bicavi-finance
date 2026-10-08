package com.bicavi.category;

import java.util.List;

// Categorias de despesa com que toda conta nova começa. Representam o TIPO do
// gasto (o que foi comprado); como foi pago fica na forma de pagamento e no cartão.
// O usuário pode renomear ou excluir qualquer uma.
// As contas que já existiam receberam a mesma lista pela migration V9.
public final class DefaultCategories {

    public static final List<String> EXPENSE = List.of(
            "Casa", "Transporte", "Saúde", "Educação", "Alimentação", "Comunicação",
            "Dívidas", "Roupas", "Cuidados pessoais", "Lazer", "Compras", "Investimentos");

    private DefaultCategories() {
    }
}
