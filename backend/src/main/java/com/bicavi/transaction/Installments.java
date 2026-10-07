package com.bicavi.transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

// Divide o total de uma compra parcelada em valores que SOMAM exatamente o total.
//
// Ex.: 100,00 em 3x -> 33,34 + 33,33 + 33,33. Dividir e arredondar cada parcela
// daria 33,33 x 3 = 99,99 (um centavo some). Por isso: todas recebem o valor
// truncado em centavos e o que sobrar vai para a 1ª parcela, como fazem as lojas.
final class Installments {

    private Installments() {
    }

    static List<BigDecimal> split(BigDecimal total, int count) {
        // RoundingMode.DOWN: trunca (33,333... -> 33,33), nunca passa do total.
        BigDecimal base = total.divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);
        BigDecimal remainder = total.subtract(base.multiply(BigDecimal.valueOf(count)));

        List<BigDecimal> amounts = new ArrayList<>(count);
        amounts.add(base.add(remainder));
        for (int i = 1; i < count; i++) {
            amounts.add(base);
        }
        return amounts;
    }
}
