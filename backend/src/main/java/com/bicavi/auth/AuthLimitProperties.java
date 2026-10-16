package com.bicavi.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Lê bicavi.auth-limit.* do application.properties.
//   loginPerEmail  tentativas de login de UM e-mail (contra adivinhar a senha de uma conta)
//   loginGlobal    tentativas de login somando TODOS os e-mails (contra um loop com
//                  e-mails sempre diferentes, que escaparia do limite por e-mail)
//   register       cadastros somando todos (contra encher o banco de contas)
@ConfigurationProperties("bicavi.auth-limit")
public record AuthLimitProperties(RateLimit loginPerEmail, RateLimit loginGlobal, RateLimit register) {

    public AuthLimitProperties {
        if (loginPerEmail == null || loginGlobal == null || register == null) {
            throw new IllegalStateException(
                    "bicavi.auth-limit precisa de login-per-email, login-global e register");
        }
    }
}
