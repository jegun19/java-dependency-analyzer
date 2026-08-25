package com.example.model;

public class Order {
    private String id;
    private String product;
    private int quantity;

    public Order(String id, String product, int quantity) {
        this.id = id;
        this.product = product;
        this.quantity = quantity;
    }

    public String getId() {
        return id;
    }
}
