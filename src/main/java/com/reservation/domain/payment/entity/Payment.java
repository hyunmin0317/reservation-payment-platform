package com.reservation.domain.payment.entity;

import com.reservation.domain.order.entity.Order;
import com.reservation.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod method;

    @Column(nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(length = 100)
    private String transactionId;

    @Builder
    public Payment(Order order, PaymentMethod method, int amount) {
        this.order = order;
        this.method = method;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
    }

    public void approve(String transactionId) {
        validateStatus(PaymentStatus.PENDING);
        this.status = PaymentStatus.APPROVED;
        this.transactionId = transactionId;
    }

    public void fail() {
        validateStatus(PaymentStatus.PENDING);
        this.status = PaymentStatus.FAILED;
    }

    public void cancel() {
        validateStatus(PaymentStatus.APPROVED);
        this.status = PaymentStatus.CANCELLED;
    }

    public void cancelFailed() {
        validateStatus(PaymentStatus.APPROVED);
        this.status = PaymentStatus.CANCEL_FAILED;
    }

    private void validateStatus(PaymentStatus expected) {
        if (this.status != expected) {
            throw new IllegalStateException(
                    String.format("결제 상태를 변경할 수 없습니다. 현재: %s, 필요: %s", this.status, expected));
        }
    }
}
