package com.example.service;

import com.example.validation.PaymentValidator;

public class PaymentService {
    private PaymentValidator validator;

    public PaymentService(PaymentValidator validator) {
        this.validator = validator;
    }

    public boolean processPayment(double amount) {
        return validator.isValid(amount);
    }
}
