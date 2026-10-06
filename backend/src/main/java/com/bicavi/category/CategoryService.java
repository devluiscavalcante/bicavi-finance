package com.bicavi.category;

import com.bicavi.common.ConflictException;
import com.bicavi.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Todo método recebe o userId do usuário logado (vindo do token, nunca do cliente).
@Service
public class CategoryService {

    private final CategoryRepository repository;

    // Injeção pelo construtor: o Spring entrega o repository pronto.
    // Nos testes, podemos passar um mock no lugar.
    public CategoryService(CategoryRepository repository) {
        this.repository = repository;
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

    @Transactional
    public CategoryResponse rename(Long userId, Long id, UpdateCategoryRequest request) {
        Category category = findOrThrow(userId, id);
        String newName = request.name().trim();

        if (!newName.equals(category.getName())) {
            ensureNameIsFree(userId, newName);
            // Não precisa chamar save(): dentro da transação, o JPA detecta
            // a mudança no objeto e faz o UPDATE no commit ("dirty checking").
            category.rename(newName);
        }
        return CategoryResponse.from(category);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        repository.delete(findOrThrow(userId, id));
    }

    // Categoria de outro usuário = "não encontrada" (404). Não revelamos que ela existe.
    private Category findOrThrow(Long userId, Long id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Categoria " + id + " não encontrada"));
    }

    private void ensureNameIsFree(Long userId, String name) {
        if (repository.existsByUserIdAndName(userId, name)) {
            throw new ConflictException("Já existe uma categoria com o nome '" + name + "'");
        }
    }
}
