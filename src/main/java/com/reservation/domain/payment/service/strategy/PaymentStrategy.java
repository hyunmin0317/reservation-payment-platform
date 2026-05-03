package com.reservation.domain.payment.service.strategy;

import com.reservation.domain.order.entity.Order;
import com.reservation.domain.payment.entity.Payment;
import com.reservation.domain.payment.entity.PaymentMethod;

public interface PaymentStrategy {

    PaymentMethod getMethod();

    void pay(Payment payment, Order order);

    void cancel(Payment payment, Order order);
}
