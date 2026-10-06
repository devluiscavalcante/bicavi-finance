package com.bicavi.category;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    // O Spring Data gera o SQL a partir do nome do método:
    // SELECT EXISTS (SELECT 1 FROM categories WHERE name = ?)
    boolean existsByName(String name);
}
