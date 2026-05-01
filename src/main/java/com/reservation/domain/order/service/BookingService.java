package com.reservation.domain.order.service;

import com.reservation.domain.order.dto.BookingRequest;
import com.reservation.domain.order.dto.BookingResponse;
import com.reservation.domain.order.entity.Order;
import com.reservation.domain.order.repository.OrderRepository;
import com.reservation.domain.payment.entity.Payment;
import com.reservation.domain.payment.service.PaymentService;
import com.reservation.domain.product.entity.Product;
import com.reservation.domain.product.service.ProductService;
import com.reservation.domain.stock.service.RedisStockService;
import com.reservation.domain.stock.service.StockService;
import com.reservation.domain.user.entity.User;
import com.reservation.domain.user.service.UserService;
import com.reservation.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final ProductService productService;
    private final UserService userService;
    private final RedisStockService redisStockService;
    private final StockService stockService;
    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    private final OrderNumberGenerator orderNumberGenerator;

    public BookingResponse book(Long userId, String idempotencyKey, BookingRequest request) {
        Product product = productService.getProduct(request.productId());
        User user = userService.getUser(userId);

        // 1. Redis 재고 차감
        redisStockService.decrease(product.getId());

        try {
            // 2. 주문 생성 + 결제 + 확정 (트랜잭션)
            return processOrder(user, product, idempotencyKey, request);
        } catch (GeneralException e) {
            // 3. 결제 실패 시 Redis 재고 복구
            redisStockService.increase(product.getId());
            throw e;
        }
    }

    @Transactional
    protected BookingResponse processOrder(User user, Product product, String idempotencyKey, BookingRequest request) {
        Order order = Order.builder()
                .orderNumber(orderNumberGenerator.generate())
                .user(user)
                .product(product)
                .totalAmount(product.getPrice())
                .idempotencyKey(idempotencyKey)
                .build();
        orderRepository.save(order);

        // 결제
        List<Payment> payments = paymentService.pay(order, request.payments());

        // DB 재고 차감 + 주문 확정
        stockService.decreaseWithPessimisticLock(product.getId());
        order.complete();

        return BookingResponse.of(order, payments);
    }
}