package com.reservation.domain.product.entity;

import com.reservation.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    public static Product create(String name, int price, LocalTime checkInTime, LocalTime checkOutTime, String description) {
        Product product = new Product();
        product.name = name;
        product.price = price;
        product.checkInTime = checkInTime;
        product.checkOutTime = checkOutTime;
        product.description = description;
        return product;
    }
}
