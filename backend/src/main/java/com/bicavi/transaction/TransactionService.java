package com.bicavi.transaction;

import com.bicavi.category.Category;
import com.bicavi.category.CategoryRepository;
import com.bicavi.common.BusinessRuleException;
import com.bicavi.common.NotFoundException;
import com.bicavi.period.PeriodPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

// Todo método recebe o userId do usuário logado (vindo do token, nunca do cliente).
// Regras de período (meses fechados e janela de consulta): ver PeriodPolicy.
@Service
public class TransactionService {

    private static final BigDecimal MIN_INSTALLMENT = new BigDecimal("0.01");

    private final TransactionRepository transactions;
    private final CategoryRepository categories;
    private final PeriodPolicy period;

    public TransactionService(TransactionRepository transactions, CategoryRepository categories,
                              PeriodPolicy period) {
        this.transactions = transactions;
        this.categories = categories;
        this.period = period;
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> list(Long userId, YearMonth month, Long categoryId) {
        period.checkVisible(month);
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
        period.checkEditable(request.occurredOn());
        Transaction tx = new Transaction(
                userId,
                findCategory(userId, request.categoryId()),
                request.amount(),
                request.type(),
                request.paymentMethod(),
                normalize(request.description()),
                request.occurredOn());
        return TransactionResponse.from(transactions.save(tx));
    }

    // Cria todas as parcelas numa ÚNICA transação de banco (@Transactional):
    // se qualquer uma falhar, nenhuma é gravada. Não sobra compra pela metade.
    @Transactional
    public List<TransactionResponse> createInstallments(Long userId, InstallmentRequest request) {
        int count = request.installments();
        // Só a 1ª precisa ser checada: as demais caem em meses posteriores, que nunca estão fechados.
        period.checkEditable(request.firstDate());
        if (request.totalAmount().compareTo(MIN_INSTALLMENT.multiply(BigDecimal.valueOf(count))) < 0) {
            throw new BusinessRuleException("O total é pequeno demais para " + count + " parcelas (mínimo R$ 0,01 cada)");
        }

        Category category = findCategory(userId, request.categoryId());
        String description = normalize(request.description());
        List<BigDecimal> amounts = Installments.split(request.totalAmount(), count);

        List<Transaction> parts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String label = "(" + (i + 1) + "/" + count + ")";
            parts.add(new Transaction(
                    userId,
                    category,
                    amounts.get(i),
                    TransactionType.EXPENSE,
                    request.paymentMethod(),
                    description == null ? "Parcela " + label : description + " " + label,
                    // Sempre a partir da 1ª data (e não "mês anterior + 1"): 31/01 vira
                    // 28/02 e depois volta a 31/03, sem ficar preso no dia 28.
                    request.firstDate().plusMonths(i)));
        }
        return transactions.saveAll(parts).stream()
                .map(TransactionResponse::from)
                .toList();
    }

    @Transactional
    public TransactionResponse update(Long userId, Long id, TransactionRequest request) {
        Transaction tx = findOrThrow(userId, id);
        // As duas datas: não dá para tirar uma transação de um mês fechado
        // nem colocar uma dentro dele.
        period.checkEditable(tx.getOccurredOn());
        period.checkEditable(request.occurredOn());
        tx.update(
                findCategory(userId, request.categoryId()),
                request.amount(),
                request.type(),
                request.paymentMethod(),
                normalize(request.description()),
                request.occurredOn());
        return TransactionResponse.from(tx);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Transaction tx = findOrThrow(userId, id);
        period.checkEditable(tx.getOccurredOn());
        transactions.delete(tx);
    }

    // Fora da janela de consulta conta como inexistente, igual à de outro usuário.
    private Transaction findOrThrow(Long userId, Long id) {
        return transactions.findByIdAndUserId(id, userId)
                .filter(tx -> period.isVisible(tx.getOccurredOn()))
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
