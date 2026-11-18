package com.bicavi.card;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Usado tanto no POST (criar) quanto no PUT (substituir todos os campos).
// Todos os campos são obrigatórios: é pelo PUT que um cartão antigo, que só
// tinha o nome, recebe os demais dados.
public record CardRequest(
        @NotBlank @Size(max = 50) String name,
        @NotNull(message = "Escolha a bandeira") CardBrand brand,
        @NotBlank(message = "Informe o nome do titular") @Size(max = 40) String holderName,
        // Só os 4 últimos dígitos: não existe campo para o número completo nem
        // para o código de segurança. @Pattern aceita null, por isso o @NotNull.
        @NotNull(message = "Informe os 4 últimos dígitos")
        @Pattern(regexp = "[0-9]{4}", message = "Informe exatamente 4 dígitos") String lastFour,
        @NotNull(message = "Informe o mês da validade") @Min(1) @Max(12) Integer expiryMonth,
        // 2000 a 2099: o cartão mostra o ano com dois dígitos ("29" = 2029).
        @NotNull(message = "Informe o ano da validade") @Min(2000) @Max(2099) Integer expiryYear,
        @NotNull(message = "Escolha a cor do cartão") CardColor color
) {
}
