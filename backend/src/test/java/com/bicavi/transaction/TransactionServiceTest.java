package com.bicavi.transaction;

import com.bicavi.category.Category;
import com.bicavi.category.CategoryRepository;
import com.bicavi.common.BusinessRuleException;
import com.bicavi.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final Long USER = 1L;
    private static final LocalDate DAY = LocalDate.of(2026, 10, 6);

    @Mock
    private TransactionRepository transactions;

    @Mock
    private CategoryRepository categories;

    @InjectMocks
    private TransactionService service;

    @Test
    void listConvertsMonthIntoHalfOpenIntervalForTheUser() {
        when(transactions.findInPeriod(any(), any(), any(), any())).thenReturn(List.of());

        service.list(USER, YearMonth.of(2026, 2), null);

        // Fevereiro de 2026 tem 28 dias: o fim exclusivo é 01/03.
        verify(transactions).findInPeriod(USER, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1), null);
    }

    @Test
    void createWithCategory() {
        Category groceries = new Category(USER, "Mercado", TransactionType.EXPENSE);
        when(categories.findByIdAndUserId(1L, USER)).thenReturn(Optional.of(groceries));
        when(transactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse response = service.create(USER,
                new TransactionRequest(new BigDecimal("35.90"), TransactionType.EXPENSE, PaymentMethod.PIX, 1L, "  Feira  ", DAY));

        assertThat(response.amount()).isEqualByComparingTo("35.90");
        assertThat(response.categoryName()).isEqualTo("Mercado");
        assertThat(response.description()).isEqualTo("Feira");
    }

    @Test
    void createWithoutCategoryDoesNotQueryCategories() {
        when(transactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse response = service.create(USER,
                new TransactionRequest(new BigDecimal("10"), TransactionType.EXPENSE, PaymentMethod.PIX, null, "  ", DAY));

        assertThat(response.categoryId()).isNull();
        assertThat(response.description()).isNull();
        verify(categories, never()).findByIdAndUserId(any(), any());
    }

    @Test
    void createWithCategoryNotOwnedByUserIsBusinessRuleViolation() {
        // Inexistente OU de outro usuário: para este usuário, dá no mesmo.
        when(categories.findByIdAndUserId(99L, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(USER,
                new TransactionRequest(new BigDecimal("10"), TransactionType.EXPENSE, PaymentMethod.PIX, 99L, null, DAY)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("99");
        verify(transactions, never()).save(any());
    }

    @Test
    void updateChangesAllFields() {
        Transaction existing = new Transaction(USER, null, new BigDecimal("10"), TransactionType.EXPENSE, PaymentMethod.PIX, "antigo", DAY);
        Category salary = new Category(USER, "Salário", TransactionType.INCOME);
        when(transactions.findByIdAndUserId(1L, USER)).thenReturn(Optional.of(existing));
        when(categories.findByIdAndUserId(2L, USER)).thenReturn(Optional.of(salary));

        TransactionResponse response = service.update(USER, 1L,
                new TransactionRequest(new BigDecimal("5000.00"), TransactionType.INCOME, null, 2L, "novo", DAY.plusDays(1)));

        assertThat(response.amount()).isEqualByComparingTo("5000.00");
        assertThat(response.type()).isEqualTo(TransactionType.INCOME);
        assertThat(response.categoryName()).isEqualTo("Salário");
        assertThat(response.occurredOn()).isEqualTo(DAY.plusDays(1));
    }

    @Test
    void updateTransactionNotOwnedByUserThrowsNotFound() {
        when(transactions.findByIdAndUserId(99L, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(USER, 99L,
                new TransactionRequest(new BigDecimal("10"), TransactionType.EXPENSE, PaymentMethod.PIX, null, null, DAY)))
                .isInstanceOf(NotFoundException.class);
    }
}
