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
import java.util.UUID;

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
                .containsOnly("Celular");
        assertThat(parts).extracting(TransactionResponse::installmentNumber).containsExactly(1, 2, 3);
        assertThat(parts).extracting(TransactionResponse::installmentCount).containsOnly(3);
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
        assertThat(parts).extracting(TransactionResponse::installmentNumber)
                .containsExactly(1, 2, 3);
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
                new TransactionRequest(new BigDecimal("5000.00"), TransactionType.INCOME, null, 2L, "novo", DAY.plusDays(1)), EditScope.THIS);

        assertThat(response.amount()).isEqualByComparingTo("5000.00");
        assertThat(response.type()).isEqualTo(TransactionType.INCOME);
        assertThat(response.categoryName()).isEqualTo("Salário");
        assertThat(response.occurredOn()).isEqualTo(DAY.plusDays(1));
    }

    @Test
    void updateTransactionNotOwnedByUserThrowsNotFound() {
        when(transactions.findByIdAndUserId(99L, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(USER, 99L,
                new TransactionRequest(new BigDecimal("10"), TransactionType.EXPENSE, PaymentMethod.PIX, null, null, DAY), EditScope.THIS))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateOfTransactionInClosedMonthIsRejected() {
        Transaction old = new Transaction(USER, null, new BigDecimal("10"), TransactionType.EXPENSE, PaymentMethod.PIX, null, CLOSED_DAY);
        when(transactions.findByIdAndUserId(1L, USER)).thenReturn(Optional.of(old));

        // Mesmo levando a data para um mês aberto: tirar do mês fechado também é alterá-lo.
        assertThatThrownBy(() -> service.update(USER, 1L,
                new TransactionRequest(new BigDecimal("20"), TransactionType.EXPENSE, PaymentMethod.PIX, null, null, DAY), EditScope.THIS))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(old.getAmount()).isEqualByComparingTo("10");
    }

    @Test
    void updateMovingTransactionIntoClosedMonthIsRejected() {
        Transaction current = new Transaction(USER, null, new BigDecimal("10"), TransactionType.EXPENSE, PaymentMethod.PIX, null, DAY);
        when(transactions.findByIdAndUserId(1L, USER)).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.update(USER, 1L,
                new TransactionRequest(new BigDecimal("10"), TransactionType.EXPENSE, PaymentMethod.PIX, null, null, CLOSED_DAY), EditScope.THIS))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(current.getOccurredOn()).isEqualTo(DAY);
    }

    @Test
    void deleteInOpenMonthRemovesTransaction() {
        Transaction current = new Transaction(USER, null, new BigDecimal("10"), TransactionType.EXPENSE, PaymentMethod.PIX, null, DAY);
        when(transactions.findByIdAndUserId(1L, USER)).thenReturn(Optional.of(current));

        service.delete(USER, 1L, EditScope.THIS);

        verify(transactions).delete(current);
    }

    @Test
    void deleteInClosedMonthIsRejected() {
        Transaction old = new Transaction(USER, null, new BigDecimal("10"), TransactionType.EXPENSE, PaymentMethod.PIX, null, CLOSED_DAY);
        when(transactions.findByIdAndUserId(1L, USER)).thenReturn(Optional.of(old));

        assertThatThrownBy(() -> service.delete(USER, 1L, EditScope.THIS)).isInstanceOf(BusinessRuleException.class);
        verify(transactions, never()).delete(any());
    }

    // ===== parcelas ligadas: "esta e as próximas" =====

    private static final UUID GROUP = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static Transaction part(int number, LocalDate date) {
        return Transaction.installment(USER, null, new BigDecimal("33.33"), PaymentMethod.CREDITO, "PS5", date,
                GROUP, number, 3);
    }

    @Test
    void updateFollowingSpreadsCategoryPaymentAndNameButNotAmountOrDate() {
        Transaction second = part(2, LocalDate.of(2026, 11, 10));
        Transaction third = part(3, LocalDate.of(2026, 12, 10));
        Category card = new Category(USER, "Cartão BB", TransactionType.EXPENSE);
        when(transactions.findByIdAndUserId(2L, USER)).thenReturn(Optional.of(second));
        when(transactions.findFollowingInstallments(USER, GROUP, 2)).thenReturn(List.of(third));
        when(categories.findByIdAndUserId(5L, USER)).thenReturn(Optional.of(card));

        service.update(USER, 2L, new TransactionRequest(new BigDecimal("40.00"), TransactionType.EXPENSE,
                PaymentMethod.DEBITO, 5L, "PS5 Pro", LocalDate.of(2026, 11, 15)), EditScope.FOLLOWING);

        // A parcela editada recebe tudo.
        assertThat(second.getAmount()).isEqualByComparingTo("40.00");
        assertThat(second.getOccurredOn()).isEqualTo(LocalDate.of(2026, 11, 15));
        // A seguinte: categoria, forma de pagamento e nome sim; valor e data não.
        assertThat(third.getCategory()).isSameAs(card);
        assertThat(third.getPaymentMethod()).isEqualTo(PaymentMethod.DEBITO);
        assertThat(third.getDescription()).isEqualTo("PS5 Pro");
        assertThat(third.getAmount()).isEqualByComparingTo("33.33");
        assertThat(third.getOccurredOn()).isEqualTo(LocalDate.of(2026, 12, 10));
    }

    @Test
    void updateThisInstallmentDoesNotLookForTheOthers() {
        Transaction second = part(2, LocalDate.of(2026, 11, 10));
        when(transactions.findByIdAndUserId(2L, USER)).thenReturn(Optional.of(second));

        service.update(USER, 2L, new TransactionRequest(new BigDecimal("33.33"), TransactionType.EXPENSE,
                PaymentMethod.PIX, null, "PS5", LocalDate.of(2026, 11, 10)), EditScope.THIS);

        assertThat(second.getPaymentMethod()).isEqualTo(PaymentMethod.PIX);
        verify(transactions, never()).findFollowingInstallments(any(), any(), any());
    }

    @Test
    void installmentCannotBecomeIncome() {
        Transaction second = part(2, LocalDate.of(2026, 11, 10));
        when(transactions.findByIdAndUserId(2L, USER)).thenReturn(Optional.of(second));

        assertThatThrownBy(() -> service.update(USER, 2L, new TransactionRequest(new BigDecimal("33.33"),
                TransactionType.INCOME, null, null, "PS5", LocalDate.of(2026, 11, 10)), EditScope.THIS))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("sempre despesas");
    }

    @Test
    void deleteFollowingRemovesThisAndTheNextInstallments() {
        Transaction second = part(2, LocalDate.of(2026, 11, 10));
        Transaction third = part(3, LocalDate.of(2026, 12, 10));
        when(transactions.findByIdAndUserId(2L, USER)).thenReturn(Optional.of(second));
        when(transactions.findFollowingInstallments(USER, GROUP, 2)).thenReturn(List.of(third));

        service.delete(USER, 2L, EditScope.FOLLOWING);

        verify(transactions).delete(second);
        verify(transactions).deleteAll(List.of(third));
    }

    @Test
    void followingScopeOnRegularTransactionActsLikeThis() {
        Transaction regular = new Transaction(USER, null, new BigDecimal("10"), TransactionType.EXPENSE, PaymentMethod.PIX, null, DAY);
        when(transactions.findByIdAndUserId(1L, USER)).thenReturn(Optional.of(regular));

        service.delete(USER, 1L, EditScope.FOLLOWING);

        verify(transactions).delete(regular);
        verify(transactions, never()).findFollowingInstallments(any(), any(), any());
    }

    @Test
    void transactionOlderThanTheConsultationWindowIsNotFound() {
        Transaction ancient = new Transaction(USER, null, new BigDecimal("10"), TransactionType.EXPENSE, PaymentMethod.PIX, null,
                LocalDate.of(2026, 3, 31));
        when(transactions.findByIdAndUserId(1L, USER)).thenReturn(Optional.of(ancient));

        assertThatThrownBy(() -> service.get(USER, 1L)).isInstanceOf(NotFoundException.class);
    }
}
