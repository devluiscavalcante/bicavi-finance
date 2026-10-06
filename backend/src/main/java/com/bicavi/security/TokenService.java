package com.bicavi.security;

import com.bicavi.user.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class TokenService {

    static final String ISSUER = "bicavi";

    private final JwtEncoder encoder;
    private final Duration expiration;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.expiration = properties.expiration();
        this.clock = clock;
    }

    public String issue(User user) {
        Instant now = clock.instant();
        // O conteúdo do JWT é LEGÍVEL por qualquer um (é só Base64).
        // Por isso vai só o id do usuário, nada sensível.
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)                           // quem emitiu
                .subject(String.valueOf(user.getId()))    // de quem é o token
                .issuedAt(now)
                .expiresAt(now.plus(expiration))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public Duration expiration() {
        return expiration;
    }
}
