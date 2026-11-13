package com.example.pm.product;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Extends the minimal Repository marker (not JpaRepository) so the interface exposes
 * ONLY the methods we use. Spring Data still implements them at runtime.
 * This makes the hand-written fake in our tests small and honest.
 */
public interface ProductRepository extends Repository<Product, Long> {

    Product save(Product product);

    Optional<Product> findById(Long id);

    List<Product> findAll();

    boolean existsBySku(String sku);

    void deleteById(Long id);
}