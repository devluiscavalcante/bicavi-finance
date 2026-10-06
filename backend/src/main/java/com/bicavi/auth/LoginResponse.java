package com.bicavi.auth;

// tokenType "Bearer": o cliente deve enviar "Authorization: Bearer <accessToken>".
// expiresIn em segundos, para a PWA saber quando pedir login de novo.
public record LoginResponse(String accessToken, String tokenType, long expiresIn) {
}
