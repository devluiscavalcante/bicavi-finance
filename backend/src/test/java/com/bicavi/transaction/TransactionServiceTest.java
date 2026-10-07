package com.bicavi.transaction;

import com.bicavi.category.Category;
import com.bicavi.category.CategoryRepository;
import com.bicavi.common.BusinessRuleException;
import com.bicavi.common.NotFoundException;
import com.bicavi.period.PeriodPolicy;
import com.bicavi.period.TestClocks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final Long USER = 1L;
    // "Hoje" nos testes: outubro aberto, setembro já fechado (passou do dia 5).
    private static final LocalDate DAY = LocalDate.of(2026, 10, 6);
    private static final LocalDate CLOSED_DAY = LocalDate.of(2026, 9, 15);

    @Mock
    private TransactionRepository transactions;

    @Mock
    private CategoryRepository categories;

    private TransactionService service;

    @BeforeEach
    void createService() {
        service = new TransactionService(transactions, categories, new PeriodPolicy(TestClocks.at(DAY)));
    }

    @Test
    void listConvertsMonthIntoHalfOpenIntervalForTheUser() {
        when(transactions.findInPeriod(any(), any(), any(), any())).thenReturn(List.of());

        service.list(USER, YearMonth.of(2027, 2), null);

        // Fevereiro de 2027 tem 28 dias: o fim exclusivo é 01/03.
        verify(transactions).findInPeriod(USER, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 3, 1), null);
    }

    @Test
    void listRejectsMonthOlderThanTheConsultationWindow() {
        assertThatThrownBy(() -> service.list(USER, YearMonth.of(2026, 3), null))
                .isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(transactions);
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
    void createInFutureMonthIsAllowed() {
        // Salário ou parcela planejados com meses de antecedência.
        when(transactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse response = service.create(USER,
                new TransactionRequest(new BigDecimal("5000"), TransactionType.INCOME, null, null, "Salário", LocalDate.of(2027, 3, 5)));

        assertThat(response.occurredOn()).isEqualTo(LocalDate.of(2027, 3, 5));
    }

    @Test
    void createInClosedMonthIsRejected() {
        assertThatThrownBy(() -> service.create(USER,
                new TransactionRequest(new BigDecimal("10"), TransactionType.EXPENSE, PaymentMethod.PIX, null, null, CLOSED_DAY)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("09/2026 está fechado");
        verify(transactions, never()).save(any());
    }

    @Test
    void createInstallmentsSplitsTotalAcrossConsecutiveMonths() {
        Category card = new Category(USER, "Cartão BB", TransactionType.EXPENSE);
        when(categories.findByIdAndUserId(5L, USER)).thenReturn(Optional.of(card));
        when(transactions.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<TransactionResponse> parts = service.createInstallments(USER, new InstallmentRequest(
                new BigDecimal("100.00"), 3, PaymentMethod.CREDITO, 5L, "  Celular  ", LocalDate.of(2026, 11, 10)));

        assertThat(parts).extracting(TransactionResponse::amount)
                .containsExactly(new BigDecimal("33.34"), new BigDecimal("33.33"), new BigDecimal("33.33"));
        assertThat(parts).extracting(TransactionResponse::occurredOn).containsExactly(
                LocalDate.of(2026, 11, 10), LocalDate.of(2026, 12, 10), LocalDate.of(2027, 1, 10));
        assertThat(parts).extracting(TransactionResponse::description)
                .containsExactly("Celular (1/3)", "Celular (2/3)", "Celular (3/3)");
        assertThat(parts).allSatisfy(tx -> {
            assertThat(tx.type()).isEqualTo(TransactionType.EXPENSE);
            assertThat(tx.paymentMethod()).isEqualTo(PaymentMethod.CREDITO);
            assertThat(tx.categoryName()).isEqualTo("Cartão BB");
        });
    }

    @Test
    void installmentOnDay31UsesLastDayOfShorterMonthsAndComesBack() {
        when(transactions.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<TransactionResponse> parts = service.createInstallments(USER, new InstallmentRequest(
                new BigDecimal("300"), 3, PaymentMethod.CREDITO, null, null, LocalDate.of(2027, 1, 31)));

        // Fevereiro de 2027 tem 28 dias; março volta para o dia 31.
        assertThat(parts).extracting(TransactionResponse::occurredOn).containsExactly(
                LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31));
        assertThat(parts).extracting(TransactionResponse::description)
                .containsExactly("Parcela (1/3)", "Parcela (2/3)", "Parcela (3/3)");
    }

    @Test
    void installmentsStartingInClosedMonthAreRejected() {
        assertThatThrownBy(() -> service.createInstallments(USER, new InstallmentRequest(
                new BigDecimal("300"), 3, PaymentMethod.CREDITO, null, null, CLOSED_DAY)))
                .isInstanceOf(BusinessRuleException.class);
        verify(transactions, never()).saveAll(any());
    }

    @Test
    void installmentsTooSmallToSplitAreRejected() {
        // 0,05 em 6x deixaria parcelas de 0,00.
        assertThatThrownBy(() -> service.createInstallments(USER, new InstallmentRequest(
                new BigDecimal("0.05"), 6, PaymentMethod.CREDITO, null, null, DAY)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("6 parcelas");
        verify(transactions, never()).saveAll(any());
    }

    @Test
    void installmentsWithoutPaymentMethodAreRejectedByTheEntity() {
        // A regra "despesa exige forma de pagamento" vem da entidade, como no create.
        assertThatThrownBy(() -> service.createInstallments(USER, new InstallmentRequest(
                new BigDecimal("300"), 3, null, null, null, DAY)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("forma de pagamento");
        verify(transactions, never()).saveAll(any());
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

    @Test
    void updateOfTransactionInClosedMonthIsRejected() {
        Transaction old = new Transaction(USER, null, new BigDecimal("10"), TransactionType.EXPENSE, PaymentMethod.PIX, null, CLOSED_DAY);
        when(transactions.findByIdAndUserId(1L, USER)).thenReturn(Optional.of(old));

        // Mesmo levando a data para um mês aberto: tirar do mês fechado também é alterá-lo.
        assertThatThrownBy(() -> service.update(USER, 1L,
                new TransactionRequest(new BigDecimal("20"), TransactionType.EXPENSE, PaymentMethod.PIX, null, null, DAY)))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(old.getAmount()).isEqualByComparingTo("10");
    }

    @Test
    void updateMovingTransactionIntoClosedMonthIsRejected() {
        Transaction current = new Transaction(USER, null, new BigDecimal("10"), TransactionType.EXPENSE, PaymentMethod.PIX, null, DAY);
        when(transactions.findByIdAndUserId(1L, USER)).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.update(USER, 1L,
                new TransactionRequest(new BigDecimal("10"), TransactionType.EXPENSE, PaymentMethod.PIX, null, null, CLOSED_DAY)))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(current.getOccurredOn()).isEqualTo(DAY);
    }

    @Test
    void deleteInOpenMonthRemovesTransaction() {
        Transaction current = new Transaction(USER, null, new BigDecimal("10"), TransactionType.EXPENSE, PaymentMethod.PIX, null, DAY);
        when(transactions.findByIdAndUserId(1L, USER)).thenReturn(Optional.of(current));

        service.delete(USER, 1L);

        verify(transactions).delete(current);
    }

    @Test
    void deleteInClosedMonthIsRejected() {
        Transaction old = new Transaction(USER, null, new BigDecimal("10"), TransactionType.EXPENSE, PaymentMethod.PIX, null, CLOSED_DAY);
        when(transactions.findByIdAndUserId(1L, USER)).thenReturn(Optional.of(old));

        assertThatThrownBy(() -> service.delete(USER, 1L)).isInstanceOf(BusinessRuleException.class);
        verify(transactions, never()).delete(any());
    }

    @Test
    void transactionOlderThanTheConsultationWindowIsNotFound() {
        Transaction ancient = new Transaction(USER, null, new BigDecimal("10"), TransactionType.EXPENSE, PaymentMethod.PIX, null,
                LocalDate.of(2026, 3, 31));
        when(transactions.findByIdAndUserId(1L, USER)).thenReturn(Optional.of(ancient));

        assertThatThrownBy(() -> service.get(USER, 1L)).isInstanceOf(NotFoundException.class);
    }
}
