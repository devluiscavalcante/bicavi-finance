package com.bicavi.card;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Usado para criar e para renomear: o cartão só tem o nome.
public record CardRequest(
        @NotBlank @Size(max = 50) String name
) {
}
