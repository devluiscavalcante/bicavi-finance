package com.bicavi.transaction;

import com.bicavi.category.Category;
import com.bicavi.category.CategoryRepository;
import com.bicavi.common.BusinessRuleException;
import com.bicavi.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;

// Todo método recebe o userId do usuário logado (vindo do token, nunca do cliente).
@Service
public class TransactionService {

    private final TransactionRepository transactions;
    private final CategoryRepository categories;

    public TransactionService(TransactionRepository transactions, CategoryRepository categories) {
        this.transactions = transactions;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> list(Long userId, YearMonth month, Long categoryId) {
        return transactions.findInPeriod(userId, month.atDay(1), month.plusMonths(1).atDay(1), categoryId)
                .stream()
                .map(TransactionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(Long userId, Long id) {
        return TransactionResponse.from(findOrThrow(userId, id));
    }

    @Transactional
    public TransactionResponse create(Long userId, TransactionRequest request) {
        Transaction tx = new Transaction(
                userId,
                findCategory(userId, request.categoryId()),
                request.amount(),
                request.type(),
                normalize(request.description()),
                request.occurredOn());
        return TransactionResponse.from(transactions.save(tx));
    }

    @Transactional
    public TransactionResponse update(Long userId, Long id, TransactionRequest request) {
        Transaction tx = findOrThrow(userId, id);
        tx.update(
                findCategory(userId, request.categoryId()),
                request.amount(),
                request.type(),
                normalize(request.description()),
                request.occurredOn());
        return TransactionResponse.from(tx);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        transactions.delete(findOrThrow(userId, id));
    }

    private Transaction findOrThrow(Long userId, Long id) {
        return transactions.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Transação " + id + " não encontrada"));
    }

    // categoryId é opcional. Se veio preenchido, precisa ser uma categoria DO USUÁRIO.
    // Categoria de outro usuário recebe a mesma resposta de uma inexistente.
    // É 400 (dado enviado errado), não 404: o recurso acessado é a transação.
    private Category findCategory(Long userId, Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categories.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new BusinessRuleException("Categoria " + categoryId + " não existe"));
    }

    private static String normalize(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }
}
