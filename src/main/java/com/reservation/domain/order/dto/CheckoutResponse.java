package com.reservation.domain.order.dto;

import com.reservation.domain.product.entity.Product;
import com.reservation.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

        @Schema(description = "판매 시작 날짜. null이면 매일 판매 시작 시간이 적용된다.", example = "2026-05-04")
        LocalDate saleStartDate,

        @Schema(description = "판매 시작 시간", example = "00:00:00")
        LocalTime saleStartTime,

        @Schema(description = "서버 기준 현재 시각", example = "2026-05-03T16:30:00")
        LocalDateTime serverTime,

        @Schema(description = "사용자 보유 포인트", example = "50000")
        int userPoint,

        @Schema(description = "이번 주문에 사용할 수 있는 최대 포인트", example = "50000")
        int maxUsablePoint,

        @Schema(description = "최대 포인트 사용 시 추가 결제 필요 금액", example = "50000")
        int requiredPaymentAmount,

        @Schema(description = "체크아웃 가능 상태", example = "AVAILABLE")
        CheckoutStatus checkoutStatus
) {

    public static CheckoutResponse of(Product product, int remainingStock, User user, LocalDateTime serverTime) {
        boolean saleOpen = product.isSaleOpenAt(serverTime);
        int maxUsablePoint = Math.min(user.getPointBalance(), product.getPrice());
        int requiredPaymentAmount = product.getPrice() - maxUsablePoint;

        return new CheckoutResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getCheckInTime(),
                product.getCheckOutTime(),
                product.getDescription(),
                remainingStock,
                product.getSaleStartDate(),
                product.getSaleStartTime(),
                serverTime,
                user.getPointBalance(),
                maxUsablePoint,
                requiredPaymentAmount,
                resolveStatus(saleOpen, remainingStock)
        );
    }

    private static CheckoutStatus resolveStatus(boolean saleOpen, int remainingStock) {
        if (!saleOpen) {
            return CheckoutStatus.WAITING;
        }
        if (remainingStock <= 0) {
            return CheckoutStatus.SOLD_OUT;
        }
        return CheckoutStatus.AVAILABLE;
    }
}
