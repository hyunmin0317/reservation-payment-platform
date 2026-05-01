package com.reservation.domain.order.dto;

import com.reservation.domain.product.entity.Product;
import com.reservation.domain.stock.entity.Stock;
import com.reservation.domain.user.entity.User;

import java.time.LocalTime;

public record CheckoutResponse(
        Long productId,
        String productName,
        int price,
        LocalTime checkInTime,
        LocalTime checkOutTime,
        String description,
        int remainingStock,
        int userPoint
) {

    public static CheckoutResponse of(Product product, Stock stock, User user) {
        return new CheckoutResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getCheckInTime(),
                product.getCheckOutTime(),
                product.getDescription(),
                stock.getRemainingQuantity(),
                user.getPointBalance()
        );
    }
}