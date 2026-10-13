package com.bicavi.transaction;

import com.bicavi.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

// Fatura = compras no crédito de um cartão no mês (ver ReportService).
// Fica no pacote transaction, e não em card, porque mexe em transações:
// card não depende de transaction, e assim continua (sem dependência circular).
@RestController
public class InvoiceController {

    private final TransactionService service;

    public InvoiceController(TransactionService service) {
        this.service = service;
    }

    // PATCH /api/cards/4/invoices/2026-11/payment  {"paid": true}
    // Marca (ou desmarca) de uma vez todas as compras da fatura.
    @PatchMapping("/api/cards/{cardId}/invoices/{month}/payment")
    public List<TransactionResponse> setPaid(@AuthenticationPrincipal Jwt jwt, @PathVariable Long cardId,
                                             @PathVariable YearMonth month,
                                             @Valid @RequestBody PaymentRequest request) {
        return service.setInvoicePaid(CurrentUser.id(jwt), cardId, month, request.paid());
    }
}
