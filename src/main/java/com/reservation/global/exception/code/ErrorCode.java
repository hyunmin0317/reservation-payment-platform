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

    // User
    USER_NOT_FOUND(404, "USER001", "사용자를 찾을 수 없습니다."),
    ;

    private final int value;
    private final String code;
    private final String message;
}
