package com.bicavi.category;

import com.bicavi.common.ConflictException;
import com.bicavi.common.NotFoundException;
import com.bicavi.period.PeriodPolicy;
import com.bicavi.transaction.TransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Todo método recebe o userId do usuário logado (vindo do token, nunca do cliente).
@Service
public class CategoryService {

    private final CategoryRepository repository;
    private final PeriodPolicy period;

    // Injeção pelo construtor: o Spring entrega o repository pronto.
    // Nos testes, podemos passar um mock no lugar.
    public CategoryService(CategoryRepository repository, PeriodPolicy period) {
        this.repository = repository;
        this.period = period;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(Long userId) {
        return repository.findByUserIdOrderByNameAsc(userId).stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(Long userId, Long id) {
        return CategoryResponse.from(findOrThrow(userId, id));
    }

    @Transactional
    public CategoryResponse create(Long userId, CreateCategoryRequest request) {
        String name = request.name().trim();
        ensureNameIsFree(userId, name);

        Category saved = repository.save(new Category(userId, name, request.type()));
        return CategoryResponse.from(saved);
    }

    // Chamado no cadastro: a conta é nova, então não há nome repetido para checar.
    @Transactional
    public void createDefaults(Long userId) {
        repository.saveAll(DefaultCategories.EXPENSE.stream()
                .map(name -> new Category(userId, name, TransactionType.EXPENSE))
                .toList());
    }

    @Transactional
    public CategoryResponse rename(Long userId, Long id, UpdateCategoryRequest request) {
        Category category = findOrThrow(userId, id);
        String newName = request.name().trim();

        if (!newName.equals(category.getName())) {
            // Só mudou maiúsculas/minúsculas ("mercado" -> "Mercado"): o nome já é
            // desta própria categoria. Checar duplicidade a encontraria e daria 409.
            if (!newName.equalsIgnoreCase(category.getName())) {
                ensureNameIsFree(userId, newName);
            }
            // Não precisa chamar save(): dentro da transação, o JPA detecta
            // a mudança no objeto e faz o UPDATE no commit ("dirty checking").
            category.rename(newName);
        }
        return CategoryResponse.from(category);
    }

    // As transações da categoria ficam "Sem categoria" (ON DELETE SET NULL).
    // Por isso não exclui categoria usada em mês fechado: a exclusão mudaria
    // esses meses, e mês fechado não muda (ver PeriodPolicy).
    @Transactional
    public void delete(Long userId, Long id) {
        Category category = findOrThrow(userId, id);
        if (repository.hasTransactionsBefore(userId, id, period.firstEditableMonth().atDay(1))) {
            throw new ConflictException("A categoria '" + category.getName()
                    + "' tem transações em meses fechados e não pode ser excluída");
        }
        repository.delete(category);
    }

    // Categoria de outro usuário = "não encontrada" (404). Não revelamos que ela existe.
    private Category findOrThrow(Long userId, Long id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Categoria " + id + " não encontrada"));
    }

    private void ensureNameIsFree(Long userId, String name) {
        if (repository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new ConflictException("Já existe uma categoria com o nome '" + name + "'");
        }
    }
}
