package com.example.pm.product;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A hand-written FAKE: a real, working, but simplified implementation backed by a Map.
 * No database involved. Lives in src/test, so it never ships to production.
 */
class InMemoryProductRepository implements ProductRepository {

    private final Map<Long, Product> store = new LinkedHashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);

    @Override
    public Product save(Product product) {
        if (product.getId() == null) {
            product.setId(sequence.incrementAndGet());
        }
        store.put(product.getId(), product);
        return product;
    }

    @Override
    public Optional<Product> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Product> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public boolean existsBySku(String sku) {
        return store.values().stream().anyMatch(p -> p.getSku().equals(sku));
    }

    @Override
    public void deleteById(Long id) {
        store.remove(id);
    }

    int count() {
        return store.size();
    }
}