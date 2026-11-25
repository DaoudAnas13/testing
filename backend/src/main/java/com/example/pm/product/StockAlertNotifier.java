package com.example.pm.product;

@FunctionalInterface
public interface StockAlertNotifier {

    void notifyLowStock(Product product);
}