package com.bicavi.category;

import com.bicavi.common.ConflictException;
import com.bicavi.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Teste unitário puro: sem Spring e sem banco. O repository é um "mock",
// um objeto falso cujo comportamento definimos em cada teste.
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository repository;

    @InjectMocks
    private CategoryService service;

    @Test
    void createTrimsNameAndSaves() {
        when(repository.existsByName("Mercado")).thenReturn(false);
        when(repository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response = service.create(new CreateCategoryRequest("  Mercado  ", CategoryType.EXPENSE));

        assertThat(response.name()).isEqualTo("Mercado");
        assertThat(response.type()).isEqualTo(CategoryType.EXPENSE);
    }

    @Test
    void createRejectsDuplicateName() {
        when(repository.existsByName("Mercado")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateCategoryRequest("Mercado", CategoryType.EXPENSE)))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void getThrowsWhenCategoryDoesNotExist() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void renameToSameNameDoesNotCheckDuplicates() {
        Category category = new Category("Mercado", CategoryType.EXPENSE);
        when(repository.findById(1L)).thenReturn(Optional.of(category));

        CategoryResponse response = service.rename(1L, new UpdateCategoryRequest("Mercado"));

        assertThat(response.name()).isEqualTo("Mercado");
        verify(repository, never()).existsByName(any());
    }

    @Test
    void renameRejectsNameOfAnotherCategory() {
        Category category = new Category("Mercado", CategoryType.EXPENSE);
        when(repository.findById(1L)).thenReturn(Optional.of(category));
        when(repository.existsByName("Transporte")).thenReturn(true);

        assertThatThrownBy(() -> service.rename(1L, new UpdateCategoryRequest("Transporte")))
                .isInstanceOf(ConflictException.class);
        assertThat(category.getName()).isEqualTo("Mercado");
    }
}
