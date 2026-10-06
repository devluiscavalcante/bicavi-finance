package com.bicavi.auth;

import jakarta.validation.constraints.NotBlank;

// Sem @Email nem @Size: no login não damos dicas sobre o formato esperado.
public record LoginRequest(
        @NotBlank String email,
        @NotBlank String password
) {
}
