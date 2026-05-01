package com.reservation.domain.order.controller;

import com.reservation.domain.order.dto.CheckoutResponse;
import com.reservation.domain.order.service.CheckoutService;
import com.reservation.global.common.constants.HeaderConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    private final CheckoutService checkoutService;

    @GetMapping("/products/{productId}")
    public ResponseEntity<CheckoutResponse> checkout(
            @PathVariable Long productId,
            @RequestHeader(HeaderConstants.USER_ID) Long userId) {
        CheckoutResponse response = checkoutService.getCheckout(productId, userId);
        return ResponseEntity.ok(response);
    }
}