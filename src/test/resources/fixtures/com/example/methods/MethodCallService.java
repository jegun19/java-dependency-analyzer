package com.example.methods;

import com.example.repository.OrderRepository;
import com.example.model.Order;

public class MethodCallService {
    private OrderRepository repository;

    public MethodCallService(OrderRepository repository) {
        this.repository = repository;
    }

    public void processOrder(String orderId) {
        Order order = repository.findById(orderId);
        repository.save(order);
    }
}
