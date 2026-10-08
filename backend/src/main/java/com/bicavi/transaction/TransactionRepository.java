package com.bicavi.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Regra: o código da aplicação só usa métodos que recebem userId
// (findById e findAll, herdados do JpaRepository, NÃO devem ser usados nos services).
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByIdAndUserId(Long id, Long userId);

    // JPQL: parecido com SQL, mas fala de entidades e atributos Java
    // (Transaction, t.occurredOn), não de tabelas e colunas.
    //
    // LEFT JOIN FETCH traz a categoria na MESMA consulta (evita o problema N+1).
    // LEFT porque transações sem categoria também devem aparecer.
    //
    // user_id + intervalo meio-aberto [start, end): usa o índice
    // idx_transactions_user_occurred_on (user_id, occurred_on).
    @Query("""
            SELECT t FROM Transaction t
            LEFT JOIN FETCH t.category c
            WHERE t.userId = :userId
              AND t.occurredOn >= :start AND t.occurredOn < :end
              AND (:categoryId IS NULL OR c.id = :categoryId)
            ORDER BY t.occurredOn DESC, t.id DESC
            """)
    List<Transaction> findInPeriod(Long userId, LocalDate start, LocalDate end, Long categoryId);

    // Parcelas da mesma compra DEPOIS da indicada (para "esta e as próximas").
    // userId no filtro, como em toda consulta: grupo de outro usuário não aparece.
    @Query("""
            SELECT t FROM Transaction t
            WHERE t.userId = :userId
              AND t.installmentGroup = :group
              AND t.installmentNumber > :number
            ORDER BY t.installmentNumber
            """)
    List<Transaction> findFollowingInstallments(Long userId, UUID group, Integer number);
}
