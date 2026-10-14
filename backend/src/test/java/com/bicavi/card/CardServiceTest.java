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

        CardResponse response = service.create(USER, new CardRequest("  Nubank  "));

        assertThat(response.name()).isEqualTo("Nubank");
        verify(repository).save(argThat(card -> card.getUserId().equals(USER)));
    }

    @Test
    void createRejectsDuplicateName() {
        when(repository.existsByUserIdAndNameIgnoreCase(USER, "Nubank")).thenReturn(true);

        assertThatThrownBy(() -> service.create(USER, new CardRequest("Nubank")))
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
    void renameChangingOnlyCaseDoesNotConflictWithItself() {
        Card card = new Card(USER, "nubank");
        when(repository.findByIdAndUserId(1L, USER)).thenReturn(Optional.of(card));

        CardResponse response = service.rename(USER, 1L, new CardRequest("Nubank"));

        assertThat(response.name()).isEqualTo("Nubank");
        verify(repository, never()).existsByUserIdAndNameIgnoreCase(anyLong(), any());
    }

    @Test
    void renameRejectsNameOfAnotherCard() {
        Card card = new Card(USER, "Nubank");
        when(repository.findByIdAndUserId(1L, USER)).thenReturn(Optional.of(card));
        when(repository.existsByUserIdAndNameIgnoreCase(USER, "Itaú")).thenReturn(true);

        assertThatThrownBy(() -> service.rename(USER, 1L, new CardRequest("Itaú")))
                .isInstanceOf(ConflictException.class);
        assertThat(card.getName()).isEqualTo("Nubank");
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
        when(repository.findByIdAndUserId(3L, USER)).thenReturn(Optional.of(new Card(USER, "Nubank")));
        when(period.firstEditableMonth()).thenReturn(YearMonth.of(2026, 10));
        when(repository.hasTransactionsBefore(USER, 3L, LocalDate.of(2026, 10, 1))).thenReturn(true);

        assertThatThrownBy(() -> service.delete(USER, 3L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("O cartão 'Nubank' tem compras em meses fechados e não pode ser excluído");
        verify(repository, never()).delete(any());
    }

    @Test
    void deleteRemovesCardUsedOnlyInOpenMonths() {
        Card card = new Card(USER, "Nubank");
        when(repository.findByIdAndUserId(3L, USER)).thenReturn(Optional.of(card));
        when(period.firstEditableMonth()).thenReturn(YearMonth.of(2026, 10));
        when(repository.hasTransactionsBefore(USER, 3L, LocalDate.of(2026, 10, 1))).thenReturn(false);

        service.delete(USER, 3L);

        verify(repository).delete(card);
    }
}
