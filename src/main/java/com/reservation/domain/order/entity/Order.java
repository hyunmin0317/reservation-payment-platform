package com.reservation.domain.order.entity;

import com.reservation.domain.product.entity.Product;
import com.reservation.domain.user.entity.User;
import com.reservation.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false, unique = true, length = 50)
    private String idempotencyKey;

    @Builder
    public Order(String orderNumber, User user, Product product, int totalAmount, String idempotencyKey) {
        this.orderNumber = orderNumber;
        this.user = user;
        this.product = product;
        this.totalAmount = totalAmount;
        this.status = OrderStatus.PENDING;
        this.idempotencyKey = idempotencyKey;
    }

    public void complete() {
        this.status = OrderStatus.COMPLETED;
    }

    public void fail() {
        this.status = OrderStatus.FAILED;
    }

    public void cancel() {
        this.status = OrderStatus.CANCELLED;
    }
}