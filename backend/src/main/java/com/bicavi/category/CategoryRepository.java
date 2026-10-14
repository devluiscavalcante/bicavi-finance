package com.bicavi.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// Regra: o código da aplicação só usa métodos que recebem userId.
// A verificação de dono faz parte da consulta (WHERE ... AND user_id = ?),
// então os dados de outro usuário nunca chegam a ser carregados.
// (findById e findAll, herdados do JpaRepository, NÃO devem ser usados nos services.)
public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByUserIdOrderByNameAsc(Long userId);

    Optional<Category> findByIdAndUserId(Long id, Long userId);

    // IgnoreCase: o Spring Data gera "UPPER(name) = UPPER(?)", então "Mercado"
    // e "mercado" contam como o mesmo nome (o índice da V6 garante o mesmo no banco).
    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    // A categoria tem transações antes de "before" (ou seja, em meses fechados)?
    // Mesma ideia do CardRepository: a consulta fica com quem será excluído.
    @Query("""
            SELECT COUNT(t) > 0 FROM Transaction t
            WHERE t.userId = :userId
              AND t.category.id = :categoryId
              AND t.occurredOn < :before
            """)
    boolean hasTransactionsBefore(Long userId, Long categoryId, LocalDate before);
}
