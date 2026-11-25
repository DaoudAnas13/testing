package com.example.pm.product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class LoggingStockAlertNotifier implements StockAlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingStockAlertNotifier.class);

    @Override
    public void notifyLowStock(Product product) {
        log.warn("Low stock for SKU {}: {} unit(s) left", product.getSku(), product.getStockQuantity());
    }
}