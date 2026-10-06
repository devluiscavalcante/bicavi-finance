package com.bicavi.common;

// Violação de uma regra de negócio (ex.: valor negativo, tipo incompatível
// com a categoria). Vira HTTP 400. Usamos uma exceção própria para não
// confundir com IllegalArgumentException lançada por bugs ou bibliotecas.
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
