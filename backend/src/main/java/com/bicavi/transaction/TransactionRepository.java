package com.bicavi.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // JPQL: parecido com SQL, mas fala de entidades e atributos Java
    // (Transaction, t.occurredOn), não de tabelas e colunas.
    //
    // LEFT JOIN FETCH traz a categoria na MESMA consulta (evita o problema N+1).
    // LEFT porque transações sem categoria também devem aparecer.
    //
    // Intervalo meio-aberto [start, end): usa o índice idx_transactions_occurred_on.
    @Query("""
            SELECT t FROM Transaction t
            LEFT JOIN FETCH t.category c
            WHERE t.occurredOn >= :start AND t.occurredOn < :end
              AND (:categoryId IS NULL OR c.id = :categoryId)
            ORDER BY t.occurredOn DESC, t.id DESC
            """)
    List<Transaction> findInPeriod(LocalDate start, LocalDate end, Long categoryId);
}
