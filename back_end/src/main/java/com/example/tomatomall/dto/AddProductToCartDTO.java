package com.example.tomatomall.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

@Data
public class AddProductToCartDTO {
    @NotNull
    @Positive
    @JsonDeserialize(using = StrictPositiveIntegerDeserializer.class)
    private Integer productId;

    @NotNull
    @Positive
    @JsonDeserialize(using = StrictPositiveIntegerDeserializer.class)
    private Integer quantity;
}
