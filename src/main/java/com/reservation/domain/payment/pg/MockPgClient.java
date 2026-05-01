package com.reservation.domain.payment.pg;

import com.reservation.domain.payment.entity.PaymentMethod;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MockPgClient implements PgClient {

    @Override
    public PgPaymentResult pay(PaymentMethod method, int amount) {
        String transactionId = "TXN-" + UUID.randomUUID().toString().substring(0, 8);
        return PgPaymentResult.success(transactionId);
    }

    @Override
    public void cancel(String transactionId) {
        // Mock: 취소 처리 완료
    }
}