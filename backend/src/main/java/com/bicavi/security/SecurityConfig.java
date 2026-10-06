package com.bicavi.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF protege sites que se autenticam por COOKIE (o navegador envia
                // o cookie sozinho). Nossa API usa token no header Authorization,
                // que o navegador nunca envia sozinho, então CSRF não se aplica.
                .csrf(csrf -> csrf.disable())
                // Stateless: o servidor não cria sessão. Cada requisição traz o token.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // TEMPORÁRIO: tudo liberado até a etapa C, quando os endpoints
                // passam a exigir JWT. Sem isso, o Spring Security bloquearia tudo agora.
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
