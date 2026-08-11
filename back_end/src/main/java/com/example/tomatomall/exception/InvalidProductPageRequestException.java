package com.example.tomatomall.exception;

public class InvalidProductPageRequestException extends RuntimeException {

    public InvalidProductPageRequestException(String message) {
        super(message);
    }
}
