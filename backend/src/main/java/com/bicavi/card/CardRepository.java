package com.bicavi.card;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CardRepository extends JpaRepository<Card, Long> {

    List<Card> findByUserIdOrderByNameAsc(Long userId);

    // Sempre por id E dono: o cartão de outro usuário "não existe" (404).
    Optional<Card> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    // O cartão tem compras antes de "before" (ou seja, em meses fechados)?
    // A consulta fica aqui, e não no TransactionRepository: o JPQL cita a entidade
    // Transaction só pelo nome, sem import, e o pacote card continua sem depender
    // de transaction (que já depende de card; seria uma dependência circular).
    @Query("""
            SELECT COUNT(t) > 0 FROM Transaction t
            WHERE t.userId = :userId
              AND t.card.id = :cardId
              AND t.occurredOn < :before
            """)
    boolean hasTransactionsBefore(Long userId, Long cardId, LocalDate before);
}
