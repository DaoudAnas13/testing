package com.example.pm.product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private static final BigDecimal MAX_PRICE_INCREASE_FACTOR = new BigDecimal("1.50");
    static final int LOW_STOCK_THRESHOLD = 5;

    private final ProductRepository repository;
    private final StockAlertNotifier notifier;

    public ProductService(ProductRepository repository, StockAlertNotifier notifier) {
        this.repository = repository;
        this.notifier = notifier;
    }

    public Product create(CreateProductRequest request) {
        validate(request.name(), request.price(), request.stockQuantity());

        if (repository.existsBySku(request.sku())) {
            throw new DuplicateSkuException(request.sku());
        }

        Product product = new Product(
                request.sku(), request.name(), request.description(),
                request.price(), request.stockQuantity());
        return repository.save(product);
    }

    @Transactional(readOnly = true)
    public Product getById(Long id) {
        return repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Product> findAll() {
        return repository.findAll();
    }

    public Product update(Long id, UpdateProductRequest request) {
        Product product = getById(id);

        if (product.getStatus() == ProductStatus.DISCONTINUED) {
            throw new BusinessRuleException("Discontinued products cannot be updated");
        }

        validate(request.name(), request.price(), request.stockQuantity());

        BigDecimal maxAllowedPrice = product.getPrice().multiply(MAX_PRICE_INCREASE_FACTOR);
        if (request.price().compareTo(maxAllowedPrice) > 0) {
            throw new BusinessRuleException("Price cannot increase by more than 50% in a single update");
        }

        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStockQuantity(request.stockQuantity());
        Product saved = repository.save(product);

        if (saved.getStockQuantity() < LOW_STOCK_THRESHOLD) {
            sendLowStockAlert(saved);
        }
        return saved;
    }

    public void delete(Long id) {
        Product product = getById(id);

        if (product.getStockQuantity() > 0) {
            throw new BusinessRuleException("Cannot delete a product that still has stock");
        }

        repository.deleteById(id);
    }

    private void sendLowStockAlert(Product product) {
        try {
            notifier.notifyLowStock(product);
        } catch (RuntimeException e) {
            // R10: alerts are best-effort; never fail the update because of them
            log.error("Failed to send low-stock alert for SKU {}", product.getSku(), e);
        }
    }

    private void validate(String name, BigDecimal price, int stockQuantity) {
        if (name == null || name.isBlank()) {
            throw new BusinessRuleException("Name must not be blank");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Price must be greater than zero");
        }
        if (stockQuantity < 0) {
            throw new BusinessRuleException("Stock quantity must not be negative");
        }
    }
}