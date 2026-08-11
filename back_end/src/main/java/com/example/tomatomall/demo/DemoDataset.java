package com.example.tomatomall.demo;

import java.util.List;

public record DemoDataset(
        long seed,
        List<DemoBook> books,
        List<DemoUser> users,
        List<DemoAdvertisement> advertisements
) {
    public DemoDataset {
        books = List.copyOf(books);
        users = List.copyOf(users);
        advertisements = List.copyOf(advertisements);
    }
}
