package com.bicavi.category;

import com.bicavi.common.ConflictException;
import com.bicavi.common.NotFoundException;
import com.bicavi.transaction.TransactionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Teste unitário puro: sem Spring e sem banco. O repository é um "mock",
// um objeto falso cujo comportamento definimos em cada teste.
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    private static final Long USER = 1L;

    @Mock
    private CategoryRepository repository;

    @InjectMocks
    private CategoryService service;

    @Test
    void createTrimsNameAndSavesForTheLoggedUser() {
        when(repository.existsByUserIdAndNameIgnoreCase(USER, "Mercado")).thenReturn(false);
        when(repository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response = service.create(USER, new CreateCategoryRequest("  Mercado  ", TransactionType.EXPENSE));

        assertThat(response.name()).isEqualTo("Mercado");
        assertThat(response.type()).isEqualTo(TransactionType.EXPENSE);
        verify(repository).save(argThat(category -> category.getUserId().equals(USER)));
    }

    @Test
    void createRejectsDuplicateNameOfSameUser() {
        when(repository.existsByUserIdAndNameIgnoreCase(USER, "Mercado")).thenReturn(true);

        assertThatThrownBy(() -> service.create(USER, new CreateCategoryRequest("Mercado", TransactionType.EXPENSE)))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void getThrowsWhenCategoryDoesNotExistForThisUser() {
        when(repository.findByIdAndUserId(99L, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(USER, 99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void renameToSameNameDoesNotCheckDuplicates() {
        Category category = new Category(USER, "Mercado", TransactionType.EXPENSE);
        when(repository.findByIdAndUserId(1L, USER)).thenReturn(Optional.of(category));

        CategoryResponse response = service.rename(USER, 1L, new UpdateCategoryRequest("Mercado"));

        assertThat(response.name()).isEqualTo("Mercado");
        verify(repository, never()).existsByUserIdAndNameIgnoreCase(anyLong(), any());
    }

    @Test
    void renameChangingOnlyCaseDoesNotConflictWithItself() {
        Category category = new Category(USER, "cartão bb", TransactionType.EXPENSE);
        when(repository.findByIdAndUserId(1L, USER)).thenReturn(Optional.of(category));

        CategoryResponse response = service.rename(USER, 1L, new UpdateCategoryRequest("Cartão BB"));

        assertThat(response.name()).isEqualTo("Cartão BB");
        verify(repository, never()).existsByUserIdAndNameIgnoreCase(anyLong(), any());
    }

    @Test
    void renameRejectsNameOfAnotherCategory() {
        Category category = new Category(USER, "Mercado", TransactionType.EXPENSE);
        when(repository.findByIdAndUserId(1L, USER)).thenReturn(Optional.of(category));
        when(repository.existsByUserIdAndNameIgnoreCase(USER, "Transporte")).thenReturn(true);

        assertThatThrownBy(() -> service.rename(USER, 1L, new UpdateCategoryRequest("Transporte")))
                .isInstanceOf(ConflictException.class);
        assertThat(category.getName()).isEqualTo("Mercado");
    }
}
