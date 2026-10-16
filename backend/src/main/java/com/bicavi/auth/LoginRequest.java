package com.bicavi.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Sem @Email: no login não damos dicas sobre o formato esperado.
// Os @Size são os mesmos tetos do cadastro (nenhuma conta válida passa deles)
// e protegem a memória: o e-mail vira chave no AuthRateLimiter, e sem limite
// cada requisição poderia guardar ali uma "chave" de até 1 MB.
public record LoginRequest(
        @NotBlank @Size(max = 255) String email,
        @NotBlank @Size(max = 72) String password
) {
}
