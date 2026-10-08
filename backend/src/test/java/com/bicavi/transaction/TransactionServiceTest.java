package com.bicavi.transaction;

import com.bicavi.card.Card;
import com.bicavi.card.CardRepository;
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
    // Cartão do usuário: compras no crédito exigem um.
    private static final Long CARD_ID = 9L;
    private static final Card NUBANK = new Card(USER, "Nubank");

    @Mock
    private TransactionRepository transactions;

    @Mock
    private CategoryRepository categories;

    @Mock
    private CardRepository cards;

    private TransactionService service;

    @BeforeEach
    void createService() {
        service = new TransactionService(transactions, categories, cards, new PeriodPolicy(TestClocks.at(DAY)));
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
                new TransactionRequest(new BigDecimal("35.90"), TransactionType.EXPENSE, PaymentMethod.PIX, null, 1L, "  Feira  ", DAY));

        assertThat(response.amount()).isEqualByComparingTo("35.90");
        assertThat(response.categoryName()).isEqualTo("Mercado");
        assertThat(response.description()).isEqualTo("Feira");
    }

    @Test
    void createWithoutCategoryDoesNotQueryCategories() {
        when(transactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse response = service.create(USER,
                new TransactionRequest(new BigDecimal("10"), TransactionType.EXPENSE, PaymentMethod.PIX, null, null, "  ", DAY));

        assertThat(response.categoryId()).isNull();
        assertThat(response.description()).isNull();
        verify(categories, never()).findByIdAndUserId(any(), any());
    }

    @Test
    void createWithCategoryNotOwnedByUserIsBusinessRuleViolation() {
        // Inexistente OU de outro usuário: para este usuário, dá no mesmo.
        when(categories.findByIdAndUserId(99L, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(USER,
                new TransactionRequest(new BigDecimal("10"), TransactionType.EXPENSE, PaymentMethod.PIX, null, 99L, null, DAY)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("99");
        verify(transactions, never()).save(any());
    }

    @Test
    void createInFutureMonthIsAllowed() {
        // Salário ou parcela planejados com meses de antecedência.
        when(transactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse response = service.create(USER,
                new TransactionRequest(new BigDecimal("5000"), TransactionType.INCOME, null, null, null, "Salário", LocalDate.of(2027, 3, 5)));

        assertThat(response.occurredOn()).isEqualTo(LocalDate.of(2027, 3, 5));
    }

    @Test
    void createInClosedMonthIsRejected() {
        assertThatThrownBy(() -> service.create(USER,
                new TransactionRequest(new BigDecimal("10"), TransactionType.EXPENSE, PaymentMethod.PIX, null, null, null, CLOSED_DAY)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("09/2026 está fechado");
        verify(transactions, never()).save(any());
    }

    // ===== cartão =====

    @Test
    void createCreditExpenseWithCardReturnsCardName() {
        when(cards.findByIdAndUserId(CARD_ID, USER)).thenReturn(Optional.of(NUBANK));
        when(transactions.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse response = service.create(USER,
                new TransactionRequest(new BigDecimal("8.00"), TransactionType.EXPENSE, PaymentMethod.CREDITO, CARD_ID, null, "Biscoito", DAY));

        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.CREDITO);
        assertThat(response.cardName()).isEqualTo("Nubank");
    }

    @Test
    void createCreditExpenseWithoutCardIsRejected() {
        assertThatThrownBy(() -> service.create(USER,
                new TransactionRequest(new BigDecimal("8.00"), TransactionType.EXPENSE, PaymentMethod.CREDITO, null, null, null, DAY)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Informe o cartão da compra no crédito");
        verify(transactions, never()).save(any());
    }

    @Test
    void createPixExpenseWithCardIsRejected() {
        when(cards.findByIdAndUserId(CARD_ID, USER)).thenReturn(Optional.of(NUBANK));

        assertThatThrownBy(() -> service.create(USER,
                new TransactionRequest(new BigDecimal("8.00"), TransactionType.EXPENSE, PaymentMethod.PIX, CARD_ID, null, null, DAY)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Cartão só pode ser informado em compras no crédito");
    }

    @Test
    void createWithUnknownOrOtherUsersCardIsRejected() {
        // O repository filtra pelo dono: o cartão de outro usuário volta vazio, igual a um inexistente.
        when(cards.findByIdAndUserId(CARD_ID, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(USER,
                new TransactionRequest(new BigDecimal("8.00"), TransactionType.EXPENSE, PaymentMethod.CREDITO, CARD_ID, null, null, DAY)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Cartão 9 não existe");
    }

    @Test
    void createInstallmentsSplitsTotalAcrossConsecutiveMonths() {
        Category electronics = new Category(USER, "Eletrônicos", TransactionType.EXPENSE);
        when(categories.findByIdAndUserId(5L, USER)).thenReturn(Optional.of(electronics));
        when(cards.findByIdAndUserId(CARD_ID, USER)).thenReturn(Optional.of(NUBANK));
        when(transactions.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<TransactionResponse> parts = service.createInstallments(USER, new InstallmentRequest(
                new BigDecimal("100.00"), 3, PaymentMethod.CREDITO, CARD_ID, 5L, "  Celular  ", LocalDate.of(2026, 11, 10)));

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
            assertThat(tx.categoryName()).isEqualTo("Eletrônicos");
            assertThat(tx.cardName()).isEqualTo("Nubank");
        });
    }

    @Test
    void installmentOnDay31UsesLastDayOfShorterMonthsAndComesBack() {
        when(cards.findByIdAndUserId(CARD_ID, USER)).thenReturn(Optional.of(NUBANK));
        when(transactions.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<TransactionResponse> parts = service.createInstallments(USER, new InstallmentRequest(
                new BigDecimal("300"), 3, PaymentMethod.CREDITO, CARD_ID, null, null, LocalDate.of(2027, 1, 31)));

        // Fevereiro de 2027 tem 28 dias; março volta para o dia 31.
        assertThat(parts).extracting(TransactionResponse::occurredOn).containsExactly(
                LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31));
        assertThat(parts).extracting(TransactionResponse::installmentNumber)
                .containsExactly(1, 2, 3);
    }

    @Test
    void installmentsStartingInClosedMonthAreRejected() {
        assertThatThrownBy(() -> service.createInstallments(USER, new InstallmentRequest(
                new BigDecimal("300"), 3, PaymentMethod.CREDITO, null, null, null, CLOSED_DAY)))
                .isInstanceOf(BusinessRuleException.class);
        verify(transactions, never()).saveAll(any());
    }

    @Test
    void installmentsTooSmallToSplitAreRejected() {
        // 0,05 em 6x deixaria parcelas de 0,00.
        assertThatThrownBy(() -> service.createInstallments(USER, new InstallmentRequest(
                new BigDecimal("0.05"), 6, PaymentMethod.CREDITO, null, null, null, DAY)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("6 parcelas");
        verify(transactions, never()).saveAll(any());
    }

    @Test
    void installmentsOnlyOnCredit() {
        // Sem forma de pagamento ou em qualquer forma que não seja crédito: recusado
        // antes de buscar categoria/cartão ou gravar qualquer parcela.
        for (PaymentMethod method : new PaymentMethod[]{null, PaymentMethod.PIX, PaymentMethod.DINHEIRO,
                PaymentMethod.DEBITO, PaymentMethod.BOLETO}) {
            assertThatThrownBy(() -> service.createInstallments(USER, new InstallmentRequest(
                    new BigDecimal("300"), 3, method, null, null, null, DAY)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Só é possível parcelar compras no crédito");
        }
        verify(transactions, never()).saveAll(any());
    }

    @Test
    void updateChangesAllFields() {
        Transaction existing = new Transaction(USER, null, new BigDecimal("10"), TransactionType.EXPENSE, PaymentMethod.PIX, null, "antigo", DAY);
        Category salary = new Category(USER, "Salário", TransactionType.INCOME);
        when(transactions.findByIdAndUserId(1L, USER)).thenReturn(Optional.of(existing));
        when(categories.findByIdAndUserId(2L, USER)).thenReturn(Optional.of(salary));

        TransactionResponse response = service.update(USER, 1L,
                new TransactionRequest(new BigDecimal("5000.00"), TransactionType.INCOME, null, null, 2L, "novo", DAY.plusDays(1)), EditScope.THIS);

        assertThat(response.amount()).isEqualByComparingTo("5000.00");
        assertThat(response.type()).isEqualTo(TransactionType.INCOME);
        assertThat(response.categoryName()).isEqualTo("Salário");
        assertThat(response.occurredOn()).isEqualTo(DAY.plusDays(1));
    }

    @Test
    void updateTransactionNotOwnedByUserThrowsNotFound() {
        when(transactions.findByIdAndUserId(99L, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(USER, 99L,
                new TransactionRequest(new BigDecimal("10"), TransactionType.EXPENSE, PaymentMethod.PIX, null, null, null, DAY), EditScope.THIS))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateOfTransactionInClosedMonthIsRejected() {
        Transaction old = new Transaction(USER, null, new BigDecimal("10"), TransactionType.EXPENSE, PaymentMethod.PIX, null, null, CLOSED_DAY);
        when(transactions.findByIdAndUserId(1L, USER)).thenReturn(Optional.of(old));

        // Mesmo levando a data para um mês aberto: tirar do mês fechado também é alterá-lo.
        assertThatThrownBy(() -> service.update(USER, 1L,
                new TransactionRequest(new BigDecimal("20"), TransactionType.EXPENSE, PaymentMethod.PIX, null, null, null, DAY), EditScope.THIS))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(old.getAmount()).isEqualByComparingTo("10");
    }

    @Test
    void updateMovingTransactionIntoClosedMonthIsRejected() {
        Transaction current = new Transaction(USER, null, new BigDecimal("10"), TransactionType.EXPENSE, PaymentMethod.PIX, null, null, DAY);
        when(transactions.findByIdAndUserId(1L, USER)).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.update(USER, 1L,
                new TransactionRequest(new BigDecimal("10"), TransactionType.EXPENSE, PaymentMethod.PIX, null, null, null, CLOSED_DAY), EditScope.THIS))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(current.getOccurredOn()).isEqualTo(DAY);
    }

    @Test
    void deleteInOpenMonthRemovesTransaction() {
        Transaction current = new Transaction(USER, null, new BigDecimal("10"), TransactionType.EXPENSE, PaymentMethod.PIX, null, null, DAY);
        when(transactions.findByIdAndUserId(1L, USER)).thenReturn(Optional.of(current));

        service.delete(USER, 1L, EditScope.THIS);

        verify(transactions).delete(current);
    }

    @Test
    void deleteInClosedMonthIsRejected() {
        Transaction old = new Transaction(USER, null, new BigDecimal("10"), TransactionType.EXPENSE, PaymentMethod.PIX, null, null, CLOSED_DAY);
        when(transactions.findByIdAndUserId(1L, USER)).thenReturn(Optional.of(old));

        assertThatThrownBy(() -> service.delete(USER, 1L, EditScope.THIS)).isInstanceOf(BusinessRuleException.class);
        verify(transactions, never()).delete(any());
    }

    // ===== parcelas ligadas: "esta e as próximas" =====

    private static final UUID GROUP = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static Transaction part(int number, LocalDate date) {
        return Transaction.installment(USER, null, new BigDecimal("33.33"), PaymentMethod.CREDITO, NUBANK, "PS5", date,
                GROUP, number, 3);
    }

    @Test
    void updateFollowingSpreadsCategoryPaymentAndNameButNotAmountOrDate() {
        Transaction second = part(2, LocalDate.of(2026, 11, 10));
        Transaction third = part(3, LocalDate.of(2026, 12, 10));
        Category games = new Category(USER, "Games", TransactionType.EXPENSE);
        when(transactions.findByIdAndUserId(2L, USER)).thenReturn(Optional.of(second));
        when(transactions.findFollowingInstallments(USER, GROUP, 2)).thenReturn(List.of(third));
        when(categories.findByIdAndUserId(5L, USER)).thenReturn(Optional.of(games));

        service.update(USER, 2L, new TransactionRequest(new BigDecimal("40.00"), TransactionType.EXPENSE,
                PaymentMethod.DEBITO, null, 5L, "PS5 Pro", LocalDate.of(2026, 11, 15)), EditScope.FOLLOWING);

        // A parcela editada recebe tudo.
        assertThat(second.getAmount()).isEqualByComparingTo("40.00");
        assertThat(second.getOccurredOn()).isEqualTo(LocalDate.of(2026, 11, 15));
        // A seguinte: categoria, forma de pagamento e nome sim; valor e data não.
        assertThat(third.getCategory()).isSameAs(games);
        assertThat(third.getPaymentMethod()).isEqualTo(PaymentMethod.DEBITO);
        // Saiu do crédito: as duas parcelas perdem o cartão.
        assertThat(second.getCard()).isNull();
        assertThat(third.getCard()).isNull();
        assertThat(third.getDescription()).isEqualTo("PS5 Pro");
        assertThat(third.getAmount()).isEqualByComparingTo("33.33");
        assertThat(third.getOccurredOn()).isEqualTo(LocalDate.of(2026, 12, 10));
    }

    @Test
    void updateFollowingSpreadsTheNewCard() {
        Transaction second = part(2, LocalDate.of(2026, 11, 10));
        Transaction third = part(3, LocalDate.of(2026, 12, 10));
        Card itau = new Card(USER, "Itaú");
        when(transactions.findByIdAndUserId(2L, USER)).thenReturn(Optional.of(second));
        when(transactions.findFollowingInstallments(USER, GROUP, 2)).thenReturn(List.of(third));
        when(cards.findByIdAndUserId(4L, USER)).thenReturn(Optional.of(itau));

        service.update(USER, 2L, new TransactionRequest(new BigDecimal("33.33"), TransactionType.EXPENSE,
                PaymentMethod.CREDITO, 4L, null, "PS5", LocalDate.of(2026, 11, 10)), EditScope.FOLLOWING);

        assertThat(second.getCard()).isSameAs(itau);
        assertThat(third.getCard()).isSameAs(itau);
    }

    @Test
    void updateThisInstallmentDoesNotLookForTheOthers() {
        Transaction second = part(2, LocalDate.of(2026, 11, 10));
        when(transactions.findByIdAndUserId(2L, USER)).thenReturn(Optional.of(second));

        service.update(USER, 2L, new TransactionRequest(new BigDecimal("33.33"), TransactionType.EXPENSE,
                PaymentMethod.PIX, null, null, "PS5", LocalDate.of(2026, 11, 10)), EditScope.THIS);

        assertThat(second.getPaymentMethod()).isEqualTo(PaymentMethod.PIX);
        verify(transactions, never()).findFollowingInstallments(any(), any(), any());
    }

    @Test
    void installmentCannotBecomeIncome() {
        Transaction second = part(2, LocalDate.of(2026, 11, 10));
        when(transactions.findByIdAndUserId(2L, USER)).thenReturn(Optional.of(second));

        assertThatThrownBy(() -> service.update(USER, 2L, new TransactionRequest(new BigDecimal("33.33"),
                TransactionType.INCOME, null, null, null, "PS5", LocalDate.of(2026, 11, 10)), EditScope.THIS))
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
        Transaction regular = new Transaction(USER, null, new BigDecimal("10"), TransactionType.EXPENSE, PaymentMethod.PIX, null, null, DAY);
        when(transactions.findByIdAndUserId(1L, USER)).thenReturn(Optional.of(regular));

        service.delete(USER, 1L, EditScope.FOLLOWING);

        verify(transactions).delete(regular);
        verify(transactions, never()).findFollowingInstallments(any(), any(), any());
    }

    @Test
    void transactionOlderThanTheConsultationWindowIsNotFound() {
        Transaction ancient = new Transaction(USER, null, new BigDecimal("10"), TransactionType.EXPENSE, PaymentMethod.PIX, null, null,
                LocalDate.of(2026, 3, 31));
        when(transactions.findByIdAndUserId(1L, USER)).thenReturn(Optional.of(ancient));

        assertThatThrownBy(() -> service.get(USER, 1L)).isInstanceOf(NotFoundException.class);
    }
}
