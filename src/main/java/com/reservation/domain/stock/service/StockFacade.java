package com.reservation.domain.stock.service;

import com.reservation.global.exception.GeneralException;
import com.reservation.global.exception.code.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockFacade {

    private final StockService stockService;

    public void decreaseWithOptimisticLock(Long productId, int maxRetries) {
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                stockService.decreaseWithOptimisticLock(productId);
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempt == maxRetries) {
                    throw new GeneralException(ErrorCode.STOCK_SOLD_OUT);
                }
                try {
                    Thread.sleep((long) (Math.random() * 10));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new GeneralException(ErrorCode.INTERNAL_SERVER_ERROR);
                }
            }
        }
    }
}