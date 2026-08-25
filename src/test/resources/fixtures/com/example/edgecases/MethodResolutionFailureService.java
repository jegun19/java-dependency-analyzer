package com.example.edgecases;

public class MethodResolutionFailureService {
    public void doSomething() {
        Object obj = new Object();
        obj.toString();
        obj.hashCode();
        obj.equals(this);
    }
}
