package com.bicavi.common;

// Falha de AUTENTICAÇÃO (não sei quem você é). Vira HTTP 401.
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
