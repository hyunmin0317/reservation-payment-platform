package com.reservation.global.exception.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INTERNAL_SERVER_ERROR(500, "COMMON000", "서버 에러, 관리자에게 문의 바랍니다."),
    BAD_REQUEST(400, "COMMON001", "잘못된 요청입니다."),
    METHOD_NOT_ALLOWED(405, "COMMON002", "지원하지 않는 Http Method 입니다."),
    NOT_FOUND(404, "COMMON003", "요청한 리소스를 찾을 수 없습니다."),
    VALIDATION_FAILED(400, "COMMON004", "입력값에 대한 검증에 실패했습니다."),

    // Product
    PRODUCT_NOT_FOUND(404, "PRODUCT001", "상품을 찾을 수 없습니다."),

    // Stock
    STOCK_NOT_FOUND(404, "STOCK001", "재고 정보를 찾을 수 없습니다."),
    STOCK_SOLD_OUT(409, "STOCK002", "재고가 부족합니다."),
    STOCK_EXCEEDED(409, "STOCK003", "재고가 총 수량을 초과할 수 없습니다."),

    // Payment
    PAYMENT_FAILED(500, "PAYMENT001", "결제에 실패했습니다."),
    INSUFFICIENT_POINTS(400, "PAYMENT002", "포인트가 부족합니다."),
    INVALID_PAYMENT_COMBINATION(400, "PAYMENT003", "외부 결제 수단은 하나만 사용할 수 있습니다."),
    PAYMENT_LIMIT_EXCEEDED(400, "PAYMENT004", "결제 한도를 초과했습니다."),
    PAYMENT_TIMEOUT(503, "PAYMENT005", "결제 요청 시간이 초과되었습니다."),
    PAYMENT_SERVICE_UNAVAILABLE(503, "PAYMENT006", "결제 서비스를 일시적으로 이용할 수 없습니다."),

    // Order
    DUPLICATE_ORDER(409, "ORDER001", "이미 처리된 요청입니다."),

    // User
    USER_NOT_FOUND(404, "USER001", "사용자를 찾을 수 없습니다."),
    ;

    private final int value;
    private final String code;
    private final String message;
}
