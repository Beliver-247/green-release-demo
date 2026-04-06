package com.greenrelease.service;

import com.greenrelease.core.model.Order;

import java.util.Arrays;
import java.util.List;

/**
 * Service for managing orders - immutable in-memory list
 */
public class OrderService {

    private static final List<Order> ORDERS = Arrays.asList(
            new Order(1, 1, "Laptop", 999.99, "COMPLETED"),
            new Order(2, 1, "Mouse", 29.99, "PENDING"),
            new Order(3, 2, "Keyboard", 79.99, "COMPLETED"),
            new Order(4, 3, "Monitor", 299.99, "SHIPPED")
    );

    /**
     * Get all orders
     */
    public List<Order> getAllOrders() {
        return ORDERS;
    }

    /**
     * Get order by ID, returns null if not found
     */
    public Order getOrderById(int id) {
        return ORDERS.stream()
                .filter(order -> order.getId() == id)
                .findFirst()
                .orElse(null);
    }

    /**
     * Get orders by user ID
     */
    public List<Order> getOrdersByUserId(int userId) {
        return ORDERS.stream()
                .filter(order -> order.getUserId() == userId)
                .toList();
    }
}
