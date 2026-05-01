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
    VALIDATION_FAILED(400, "COMMON003", "입력값에 대한 검증에 실패했습니다."),
    ;

    private final int value;
    private final String code;
    private final String message;
}
