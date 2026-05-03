package com.reservation.domain.order.service;

import com.reservation.domain.order.entity.Order;
import com.reservation.domain.order.repository.OrderRepository;
import com.reservation.domain.payment.entity.Payment;
import com.reservation.domain.payment.service.PaymentService;
import com.reservation.domain.stock.service.StockService;
import com.reservation.domain.order.dto.BookingRequest;
import com.reservation.domain.product.entity.Product;
import com.reservation.domain.user.entity.User;
import com.reservation.domain.user.repository.UserRepository;
import com.reservation.global.exception.GeneralException;
import com.reservation.global.exception.code.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderTransactionService {

    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    private final StockService stockService;
    private final OrderNumberGenerator orderNumberGenerator;
    private final UserRepository userRepository;

    @Transactional
    public OrderResult process(Long userId, Product product, String idempotencyKey, BookingRequest request, boolean decreaseDbStock) {
        User user = userRepository.findWithLockById(userId)
                .orElseThrow(() -> new GeneralException(ErrorCode.USER_NOT_FOUND));

        Order order = Order.builder()
                .orderNumber(orderNumberGenerator.generate())
                .user(user)
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

    public void logFailure(Long userId, Long productId, String idempotencyKey, String reason) {
        log.warn("결제 실패 (userId: {}, productId: {}, idempotencyKey: {}, reason: {})",
                userId, productId, idempotencyKey, reason);
    }

    public record OrderResult(Order order, List<Payment> payments) {
    }
}
