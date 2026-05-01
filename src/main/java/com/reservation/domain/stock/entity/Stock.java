package com.reservation.domain.stock.entity;

import com.reservation.domain.product.entity.Product;
import com.reservation.global.common.entity.BaseEntity;
import com.reservation.global.exception.GeneralException;
import com.reservation.global.exception.code.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(nullable = false)
    private int totalQuantity;

    @Column(nullable = false)
    private int remainingQuantity;

    @Version
    private int version;

    public static Stock create(Product product, int totalQuantity) {
        Stock stock = new Stock();
        stock.product = product;
        stock.totalQuantity = totalQuantity;
        stock.remainingQuantity = totalQuantity;
        return stock;
    }

    public void decrease() {
        if (this.remainingQuantity <= 0) {
            throw new GeneralException(ErrorCode.STOCK_SOLD_OUT);
        }
        this.remainingQuantity--;
    }

    public void increase() {
        if (this.remainingQuantity >= this.totalQuantity) {
            throw new GeneralException(ErrorCode.STOCK_EXCEEDED);
        }
        this.remainingQuantity++;
    }
}
