package com.bicavi.card;

import com.bicavi.common.ConflictException;
import com.bicavi.common.NotFoundException;
import com.bicavi.period.PeriodPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardServiceTest {

    private static final Long USER = 1L;

    @Mock
    private CardRepository repository;

    @Mock
    private PeriodPolicy period;

    @InjectMocks
    private CardService service;

    @Test
    void createTrimsNameAndSavesForTheLoggedUser() {
        when(repository.existsByUserIdAndNameIgnoreCase(USER, "Nubank")).thenReturn(false);
        when(repository.save(any(Card.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CardResponse response = service.create(USER, TestCards.request("  Nubank  "));

        assertThat(response.name()).isEqualTo("Nubank");
        verify(repository).save(argThat(card -> card.getUserId().equals(USER)));
    }

    @Test
    void createSavesBrandHolderLastFourExpiryAndColor() {
        when(repository.save(any(Card.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CardRequest request = new CardRequest("Nubank", CardBrand.MASTERCARD, "  Maria Silva dos Santos  ",
                "0042", 3, 2031, CardColor.ROXO);

        CardResponse response = service.create(USER, request);

        assertThat(response.brand()).isEqualTo(CardBrand.MASTERCARD);
        // O nome do titular também perde os espaços das pontas.
        assertThat(response.holderName()).isEqualTo("Maria Silva dos Santos");
        // "0042" continua com os zeros: é texto, não número.
        assertThat(response.lastFour()).isEqualTo("0042");
        assertThat(response.expiryMonth()).isEqualTo(3);
        assertThat(response.expiryYear()).isEqualTo(2031);
        assertThat(response.color()).isEqualTo(CardColor.ROXO);
    }

    @Test
    void createRejectsDuplicateName() {
        when(repository.existsByUserIdAndNameIgnoreCase(USER, "Nubank")).thenReturn(true);

        assertThatThrownBy(() -> service.create(USER, TestCards.request("Nubank")))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void getThrowsWhenCardDoesNotExistForThisUser() {
        when(repository.findByIdAndUserId(99L, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(USER, 99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Cartão 99 não encontrado");
    }

    @Test
    void updateReplacesAllTheData() {
        Card card = TestCards.card(USER, "Nubank");
        when(repository.findByIdAndUserId(1L, USER)).thenReturn(Optional.of(card));
        when(repository.existsByUserIdAndNameIgnoreCase(USER, "Nubank Ultravioleta")).thenReturn(false);
        CardRequest request = new CardRequest("Nubank Ultravioleta", CardBrand.MASTERCARD, "  João Souza ",
                "9876", 1, 2032, CardColor.ROXO);

        CardResponse response = service.update(USER, 1L, request);

        assertThat(response).isEqualTo(new CardResponse(null, "Nubank Ultravioleta", CardBrand.MASTERCARD,
                "João Souza", "9876", 1, 2032, CardColor.ROXO));
        // A própria entidade mudou: é ela que o Hibernate grava no fim da transação.
        assertThat(card.getLastFour()).isEqualTo("9876");
    }

    // Editar só a validade (o caso mais comum) não pode acusar "nome repetido".
    @Test
    void updateKeepingTheNameDoesNotCheckForConflict() {
        Card card = TestCards.card(USER, "Nubank");
        when(repository.findByIdAndUserId(1L, USER)).thenReturn(Optional.of(card));
        CardRequest request = new CardRequest("Nubank", CardBrand.VISA, "Maria Silva", "1234", 5, 2034,
                CardColor.PRETO);

        CardResponse response = service.update(USER, 1L, request);

        assertThat(response.expiryMonth()).isEqualTo(5);
        assertThat(response.expiryYear()).isEqualTo(2034);
        verify(repository, never()).existsByUserIdAndNameIgnoreCase(anyLong(), any());
    }

    @Test
    void updateChangingOnlyCaseOfTheNameDoesNotConflictWithItself() {
        Card card = TestCards.card(USER, "nubank");
        when(repository.findByIdAndUserId(1L, USER)).thenReturn(Optional.of(card));

        CardResponse response = service.update(USER, 1L, TestCards.request("Nubank"));

        assertThat(response.name()).isEqualTo("Nubank");
        verify(repository, never()).existsByUserIdAndNameIgnoreCase(anyLong(), any());
    }

    @Test
    void updateRejectsNameOfAnotherCardAndChangesNothing() {
        Card card = TestCards.card(USER, "Nubank");
        when(repository.findByIdAndUserId(1L, USER)).thenReturn(Optional.of(card));
        when(repository.existsByUserIdAndNameIgnoreCase(USER, "Itaú")).thenReturn(true);
        CardRequest request = new CardRequest("Itaú", CardBrand.ELO, "Outra Pessoa", "0000", 1, 2040,
                CardColor.AURORA);

        assertThatThrownBy(() -> service.update(USER, 1L, request))
                .isInstanceOf(ConflictException.class);
        assertThat(card.getName()).isEqualTo("Nubank");
        assertThat(card.getBrand()).isEqualTo(CardBrand.VISA);
        assertThat(card.getLastFour()).isEqualTo("1234");
    }

    @Test
    void updateOfAnotherUsersCardThrows() {
        when(repository.findByIdAndUserId(5L, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(USER, 5L, TestCards.request("Nubank")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteOfAnotherUsersCardThrowsAndDeletesNothing() {
        when(repository.findByIdAndUserId(5L, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(USER, 5L)).isInstanceOf(NotFoundException.class);
        verify(repository, never()).delete(any());
    }

    // Excluir deixaria as compras de meses fechados "sem cartão": mudaria o passado.
    @Test
    void deleteRejectsCardUsedInClosedMonth() {
        when(repository.findByIdAndUserId(3L, USER)).thenReturn(Optional.of(TestCards.card(USER, "Nubank")));
        when(period.firstEditableMonth()).thenReturn(YearMonth.of(2026, 10));
        when(repository.hasTransactionsBefore(USER, 3L, LocalDate.of(2026, 10, 1))).thenReturn(true);

        assertThatThrownBy(() -> service.delete(USER, 3L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("O cartão 'Nubank' tem compras em meses fechados e não pode ser excluído");
        verify(repository, never()).delete(any());
    }

    @Test
    void deleteRemovesCardUsedOnlyInOpenMonths() {
        Card card = TestCards.card(USER, "Nubank");
        when(repository.findByIdAndUserId(3L, USER)).thenReturn(Optional.of(card));
        when(period.firstEditableMonth()).thenReturn(YearMonth.of(2026, 10));
        when(repository.hasTransactionsBefore(USER, 3L, LocalDate.of(2026, 10, 1))).thenReturn(false);

        service.delete(USER, 3L);

        verify(repository).delete(card);
    }
}
