package com.example.validation;

import com.example.processing.PaymentProcessor;

public class PaymentValidator {
    private PaymentProcessor processor;

    public PaymentValidator(PaymentProcessor processor) {
        this.processor = processor;
    }

    public boolean isValid(double amount) {
        return amount > 0 && processor.canProcess(amount);
    }
}
