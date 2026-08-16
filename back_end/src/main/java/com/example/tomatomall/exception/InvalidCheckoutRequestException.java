package com.example.tomatomall.exception;

public class InvalidCheckoutRequestException extends RuntimeException {
    public InvalidCheckoutRequestException(String message) {
        super(message);
    }
}
