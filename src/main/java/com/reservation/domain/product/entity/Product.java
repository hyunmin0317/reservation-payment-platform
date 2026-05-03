package com.reservation.domain.product.entity;

import com.reservation.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private LocalTime checkInTime;

    @Column(nullable = false)
    private LocalTime checkOutTime;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private LocalTime saleStartTime;

    private LocalDate saleStartDate;

    public static Product create(String name, int price, LocalTime checkInTime, LocalTime checkOutTime, String description) {
        return create(name, price, checkInTime, checkOutTime, description, LocalTime.MIDNIGHT);
    }

    public static Product create(String name, int price, LocalTime checkInTime, LocalTime checkOutTime, String description, LocalTime saleStartTime) {
        Product product = new Product();
        product.name = name;
        product.price = price;
        product.checkInTime = checkInTime;
        product.checkOutTime = checkOutTime;
        product.description = description;
        product.saleStartTime = saleStartTime;
        return product;
    }

    public boolean isSaleOpen() {
        LocalDateTime now = LocalDateTime.now();
        if (saleStartDate != null) {
            return !now.isBefore(LocalDateTime.of(saleStartDate, saleStartTime));
        }
        return !now.toLocalTime().isBefore(saleStartTime);
    }
}
