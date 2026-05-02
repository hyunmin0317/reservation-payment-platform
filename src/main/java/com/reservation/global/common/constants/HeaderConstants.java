package com.reservation.global.common.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class HeaderConstants {

    public static final String USER_ID = "X-User-Id";
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
}