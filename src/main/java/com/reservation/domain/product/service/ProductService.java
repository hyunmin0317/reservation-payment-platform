package com.reservation.domain.product.service;

import com.reservation.domain.product.entity.Product;
import com.reservation.domain.product.repository.ProductRepository;
import com.reservation.global.exception.GeneralException;
import com.reservation.global.exception.code.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    public Product getProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new GeneralException(ErrorCode.PRODUCT_NOT_FOUND));
    }
}
