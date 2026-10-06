package com.bicavi.transaction;

import com.bicavi.category.Category;
import com.bicavi.category.CategoryRepository;
import com.bicavi.common.BusinessRuleException;
import com.bicavi.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;

@Service
public class TransactionService {

    private final TransactionRepository transactions;
    private final CategoryRepository categories;

    public TransactionService(TransactionRepository transactions, CategoryRepository categories) {
        this.transactions = transactions;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> list(YearMonth month, Long categoryId) {
        return transactions.findInPeriod(month.atDay(1), month.plusMonths(1).atDay(1), categoryId)
                .stream()
                .map(TransactionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(Long id) {
        return TransactionResponse.from(findOrThrow(id));
    }

    @Transactional
    public TransactionResponse create(TransactionRequest request) {
        Transaction tx = new Transaction(
                findCategory(request.categoryId()),
                request.amount(),
                request.type(),
                normalize(request.description()),
                request.occurredOn());
        return TransactionResponse.from(transactions.save(tx));
    }

    @Transactional
    public TransactionResponse update(Long id, TransactionRequest request) {
        Transaction tx = findOrThrow(id);
        tx.update(
                findCategory(request.categoryId()),
                request.amount(),
                request.type(),
                normalize(request.description()),
                request.occurredOn());
        return TransactionResponse.from(tx);
    }

    @Transactional
    public void delete(Long id) {
        transactions.delete(findOrThrow(id));
    }

    private Transaction findOrThrow(Long id) {
        return transactions.findById(id)
                .orElseThrow(() -> new NotFoundException("Transação " + id + " não encontrada"));
    }

    // categoryId é opcional. Se veio preenchido, a categoria precisa existir.
    // É 400 (dado enviado errado), não 404: o recurso acessado é a transação.
    private Category findCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categories.findById(categoryId)
                .orElseThrow(() -> new BusinessRuleException("Categoria " + categoryId + " não existe"));
    }

    private static String normalize(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }
}
