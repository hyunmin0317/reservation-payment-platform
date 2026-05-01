package com.reservation.domain.order.service;

import com.reservation.domain.order.dto.CheckoutResponse;
import com.reservation.domain.product.entity.Product;
import com.reservation.domain.product.service.ProductService;
import com.reservation.domain.stock.entity.Stock;
import com.reservation.domain.stock.service.StockService;
import com.reservation.domain.user.entity.User;
import com.reservation.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CheckoutService {

    private final ProductService productService;
    private final StockService stockService;
    private final UserService userService;

    public CheckoutResponse getCheckout(Long productId, Long userId) {
        Product product = productService.getProduct(productId);
        Stock stock = stockService.getStockByProductId(productId);
        User user = userService.getUser(userId);
        return CheckoutResponse.of(product, stock, user);
    }
}