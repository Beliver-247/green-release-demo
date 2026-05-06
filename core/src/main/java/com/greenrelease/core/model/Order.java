package com.greenrelease.core.model;

/**
 * Order domain model - pure business object with no Spring dependencies
 */
//comment for testing purposes of pipeline
public class Order {
    private final int id;
    private final int userId;
    private final String product;
    private final double amount;
    private final String status;

    public Order(int id, int userId, String product, double amount, String status) {
        this.id = id;
        this.userId = userId;
        this.product = product;
        this.amount = amount;
        this.status = status;
    }

    public int getId() {
        return id;
    }

    public int getUserId() {
        return userId;
    }

    public String getProduct() {
        return product;
    }

    public double getAmount() {
        return amount;
    }

    public String getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return "Order{" +
                "id=" + id +
                ", userId=" + userId +
                ", product='" + product + '\'' +
                ", amount=" + amount +
                ", status='" + status + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Order)) return false;

        Order order = (Order) o;

        if (id != order.id) return false;
        if (userId != order.userId) return false;
        if (Double.compare(order.amount, amount) != 0) return false;
        if (!product.equals(order.product)) return false;
        System.out.println("Comparing order statuses: " + status + " vs " + order.status);
        return status.equals(order.status);
    }

    @Override
    public int hashCode() {
        int result = id;
        result = 31 * result + userId;
        result = 31 * result + product.hashCode();
        result = 31 * result + (int) Double.doubleToLongBits(amount);
        result = 31 * result + status.hashCode();
        return result;
    }
}
