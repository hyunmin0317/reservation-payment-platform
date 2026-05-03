package com.reservation.domain.order.service;

import com.reservation.domain.order.entity.Order;
import com.reservation.domain.order.repository.OrderRepository;
import com.reservation.domain.payment.entity.Payment;
import com.reservation.domain.payment.service.PaymentService;
import com.reservation.domain.stock.service.StockService;
import com.reservation.domain.order.dto.BookingRequest;
import com.reservation.domain.product.entity.Product;
import com.reservation.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderTransactionService {

    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    private final StockService stockService;
    private final OrderNumberGenerator orderNumberGenerator;
    private final UserService userService;

    @Transactional
    public OrderResult process(Long userId, Product product, String idempotencyKey, BookingRequest request, boolean decreaseDbStock) {
        Order order = Order.builder()
                .orderNumber(orderNumberGenerator.generate())
                .user(userService.getUser(userId))
                .product(product)
                .totalAmount(product.getPrice())
                .idempotencyKey(idempotencyKey)
                .build();
        orderRepository.save(order);

        List<Payment> payments = paymentService.pay(order, request.payments(), product.getPrice());

        if (decreaseDbStock) {
            stockService.decreaseWithPessimisticLock(product.getId());
        }
        order.complete();

        return new OrderResult(order, payments);
    }

    public record OrderResult(Order order, List<Payment> payments) {
    }
}
