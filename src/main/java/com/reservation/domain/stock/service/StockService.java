package com.reservation.domain.stock.service;

import com.reservation.domain.stock.entity.Stock;
import com.reservation.domain.stock.repository.StockRepository;
import com.reservation.global.exception.GeneralException;
import com.reservation.global.exception.code.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockService {

    private final StockRepository stockRepository;

    public int getRemainingQuantity(Long productId) {
        Stock stock = stockRepository.findByProductId(productId)
                .orElseThrow(() -> new GeneralException(ErrorCode.STOCK_NOT_FOUND));
        return stock.getRemainingQuantity();
    }

    @Transactional
    public void decreaseWithPessimisticLock(Long productId) {
        Stock stock = stockRepository.findWithLockByProductId(productId)
                .orElseThrow(() -> new GeneralException(ErrorCode.STOCK_NOT_FOUND));

        stock.decrease();
    }

    @Transactional
    public void increaseWithPessimisticLock(Long productId) {
        Stock stock = stockRepository.findWithLockByProductId(productId)
                .orElseThrow(() -> new GeneralException(ErrorCode.STOCK_NOT_FOUND));

        stock.increase();
    }
}
