package com.bicavi.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// Regra: o código da aplicação só usa métodos que recebem userId.
// A verificação de dono faz parte da consulta (WHERE ... AND user_id = ?),
// então os dados de outro usuário nunca chegam a ser carregados.
// (findById e findAll, herdados do JpaRepository, NÃO devem ser usados nos services.)
public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByUserIdOrderByNameAsc(Long userId);

    Optional<Category> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndName(Long userId, String name);
}
