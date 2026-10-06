package com.bicavi.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

// Chamado pelo Spring Security quando a requisição não está autenticada
// (sem token, token inválido, adulterado ou vencido). Isso acontece num FILTRO,
// antes do controller, então o GlobalExceptionHandler não participa.
// Aqui deixamos a resposta no mesmo formato ProblemDetail do resto da API.
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    // O padrão do Spring: status 401 + header WWW-Authenticate (RFC 6750).
    private final BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        bearer.commence(request, response, authException);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", "Unauthorized");
        body.put("status", HttpServletResponse.SC_UNAUTHORIZED);
        // Mensagem genérica: não ajudamos um atacante a saber o que errou no token.
        body.put("detail", "Autenticação necessária: token ausente, inválido ou expirado");
        body.put("instance", request.getRequestURI());

        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        JSON.writeValue(response.getWriter(), body);
    }
}
