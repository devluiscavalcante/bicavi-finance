package com.bicavi.category;

import com.bicavi.common.ConflictException;
import com.bicavi.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository repository;

    // Injeção pelo construtor: o Spring entrega o repository pronto.
    // Nos testes, podemos passar um mock no lugar.
    public CategoryService(CategoryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return repository.findAll().stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(Long id) {
        return CategoryResponse.from(findOrThrow(id));
    }

    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        String name = request.name().trim();
        ensureNameIsFree(name);

        Category saved = repository.save(new Category(name, request.type()));
        return CategoryResponse.from(saved);
    }

    @Transactional
    public CategoryResponse rename(Long id, UpdateCategoryRequest request) {
        Category category = findOrThrow(id);
        String newName = request.name().trim();

        if (!newName.equals(category.getName())) {
            ensureNameIsFree(newName);
            // Não precisa chamar save(): dentro da transação, o JPA detecta
            // a mudança no objeto e faz o UPDATE no commit ("dirty checking").
            category.rename(newName);
        }
        return CategoryResponse.from(category);
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(findOrThrow(id));
    }

    private Category findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Categoria " + id + " não encontrada"));
    }

    private void ensureNameIsFree(String name) {
        if (repository.existsByName(name)) {
            throw new ConflictException("Já existe uma categoria com o nome '" + name + "'");
        }
    }
}
