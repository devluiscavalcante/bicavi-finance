package com.bicavi.transaction;

import com.bicavi.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    // GET /api/transactions?month=2026-10&categoryId=3
    // O Spring converte "2026-10" em YearMonth automaticamente.
    @GetMapping
    public List<TransactionResponse> list(@AuthenticationPrincipal Jwt jwt,
                                          @RequestParam YearMonth month,
                                          @RequestParam(required = false) Long categoryId) {
        return service.list(CurrentUser.id(jwt), month, categoryId);
    }

    @GetMapping("/{id}")
    public TransactionResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.get(CurrentUser.id(jwt), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse create(@AuthenticationPrincipal Jwt jwt,
                                      @Valid @RequestBody TransactionRequest request) {
        return service.create(CurrentUser.id(jwt), request);
    }

    // POST /api/transactions/installments: cria as N parcelas de uma compra de uma vez.
    @PostMapping("/installments")
    @ResponseStatus(HttpStatus.CREATED)
    public List<TransactionResponse> createInstallments(@AuthenticationPrincipal Jwt jwt,
                                                        @Valid @RequestBody InstallmentRequest request) {
        return service.createInstallments(CurrentUser.id(jwt), request);
    }

    // ?scope=FOLLOWING (parcelas): aplica também às parcelas seguintes. Padrão: só esta.
    @PutMapping("/{id}")
    public TransactionResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                      @Valid @RequestBody TransactionRequest request,
                                      @RequestParam(defaultValue = "THIS") EditScope scope) {
        return service.update(CurrentUser.id(jwt), id, request, scope);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                       @RequestParam(defaultValue = "THIS") EditScope scope) {
        service.delete(CurrentUser.id(jwt), id, scope);
    }
}
