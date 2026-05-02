package com.reservation.domain.order.controller;

import com.reservation.domain.order.dto.CheckoutResponse;
import com.reservation.domain.order.service.CheckoutService;
import com.reservation.global.common.constants.HeaderConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Checkout", description = "체크아웃 API")
@RestController
@RequestMapping("/api/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    private final CheckoutService checkoutService;

    @Operation(summary = "체크아웃 정보 조회", description = "상품 정보, 잔여 재고, 사용자 포인트를 조회한다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "상품 또는 사용자를 찾을 수 없음")
    @GetMapping("/products/{productId}")
    public ResponseEntity<CheckoutResponse> checkout(
            @Parameter(description = "상품 ID") @PathVariable Long productId,
            @Parameter(description = "사용자 ID", required = true) @RequestHeader(HeaderConstants.USER_ID) Long userId) {
        CheckoutResponse response = checkoutService.getCheckout(productId, userId);
        return ResponseEntity.ok(response);
    }
}