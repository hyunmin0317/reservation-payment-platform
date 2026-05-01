package com.reservation.domain.order.service;

import com.reservation.domain.order.dto.BookingRequest;
import com.reservation.domain.order.dto.BookingResponse;
import com.reservation.domain.order.entity.Order;
import com.reservation.domain.order.repository.OrderRepository;
import com.reservation.domain.order.service.OrderTransactionService.OrderResult;
import com.reservation.domain.payment.entity.Payment;
import com.reservation.domain.payment.repository.PaymentRepository;
import com.reservation.domain.product.entity.Product;
import com.reservation.domain.product.service.ProductService;
import com.reservation.domain.stock.service.RedisStockService;
import com.reservation.domain.user.entity.User;
import com.reservation.domain.user.service.UserService;
import com.reservation.global.exception.GeneralException;
import com.reservation.global.exception.code.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final ProductService productService;
    private final UserService userService;
    private final RedisStockService redisStockService;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OrderTransactionService orderTransactionService;
    private final IdempotencyService idempotencyService;

    public BookingResponse book(Long userId, String idempotencyKey, BookingRequest request) {
        if (idempotencyService.exists(idempotencyKey)) {
            return getExistingOrder(idempotencyKey);
        }

        Product product = productService.getProduct(request.productId());
        User user = userService.getUser(userId);

        boolean redisUsed = redisStockService.decrease(product.getId());

        try {
            OrderResult result = orderTransactionService.process(user, product, idempotencyKey, request, redisUsed);
            idempotencyService.save(idempotencyKey);
            return BookingResponse.of(result.order(), result.payments());
        } catch (Exception e) {
            if (redisUsed) {
                redisStockService.increase(product.getId());
            }
            orderTransactionService.markFailed(idempotencyKey);
            throw e;
        }
    }

    private BookingResponse getExistingOrder(String idempotencyKey) {
        Order order = orderRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new GeneralException(ErrorCode.DUPLICATE_ORDER));
        List<Payment> payments = paymentRepository.findByOrderId(order.getId());
        return BookingResponse.of(order, payments);
    }
}
