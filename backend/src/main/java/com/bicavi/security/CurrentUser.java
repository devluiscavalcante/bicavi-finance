package com.bicavi.security;

import org.springframework.security.oauth2.jwt.Jwt;

// Extrai o id do usuário logado do JWT já validado pelo Spring Security.
// O "sub" (subject) foi preenchido com o id do usuário no login (TokenService).
// O userId SEMPRE vem daqui, nunca do corpo ou da URL da requisição.
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Long id(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
