package com.example.tomatomall.demo;

public record DemoUser(
        String username,
        String name,
        String role,
        String email,
        String location,
        int points
) {
}
