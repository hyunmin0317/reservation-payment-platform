package com.reservation.domain.order.dto;

import com.reservation.domain.product.entity.Product;
import com.reservation.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalTime;

@Schema(description = "체크아웃 정보 응답")
public record CheckoutResponse(
        @Schema(description = "상품 ID", example = "1")
        Long productId,

        @Schema(description = "상품명", example = "디럭스 더블룸")
        String productName,

        @Schema(description = "가격", example = "100000")
        int price,

        @Schema(description = "체크인 시간", example = "15:00:00")
        LocalTime checkInTime,

        @Schema(description = "체크아웃 시간", example = "11:00:00")
        LocalTime checkOutTime,

        @Schema(description = "상품 설명", example = "오션뷰 디럭스 더블룸")
        String description,

        @Schema(description = "잔여 재고 수량", example = "5")
        int remainingStock,

        @Schema(description = "판매 시작 시간", example = "00:00:00")
        LocalTime saleStartTime,

        @Schema(description = "사용자 보유 포인트", example = "50000")
        int userPoint,

        @Schema(description = "판매 가능 여부", example = "true")
        boolean saleOpen
) {

    public static CheckoutResponse of(Product product, int remainingStock, User user) {
        return new CheckoutResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getCheckInTime(),
                product.getCheckOutTime(),
                product.getDescription(),
                remainingStock,
                product.getSaleStartTime(),
                user.getPointBalance(),
                product.isSaleOpen()
        );
    }
}
