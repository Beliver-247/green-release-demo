package com.greenrelease.core;

import org.junit.Test;
import com.greenrelease.core.model.Order;

import static org.junit.Assert.*;

/**
 * Unit tests for Order domain model
 */
public class OrderTest {

    @Test
    public void testOrderCreation() {
        Order order = new Order(1, 100, "Laptop", 999.99, "PENDING");

        assertEquals(1, order.getId());
        assertEquals(100, order.getUserId());
        assertEquals("Laptop", order.getProduct());
        assertEquals(999.99, order.getAmount(), 0.01);
        assertEquals("PENDING", order.getStatus());
    }

    @Test
    public void testOrderEquality() {
        Order order1 = new Order(1, 100, "Laptop", 999.99, "PENDING");
        Order order2 = new Order(1, 100, "Laptop", 999.99, "PENDING");

        assertEquals(order1, order2);
        assertEquals(order1.hashCode(), order2.hashCode());
    }

    @Test
    public void testOrderInequality() {
        Order order1 = new Order(1, 100, "Laptop", 999.99, "PENDING");
        Order order2 = new Order(2, 101, "Phone", 599.99, "COMPLETED");

        assertNotEquals(order1, order2);
    }
}
