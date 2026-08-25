package com.example.methods;

public class UnresolvableCallService {
    public void doSomething() {
        new Object().hashCode();
        new Object().toString();
    }
}
