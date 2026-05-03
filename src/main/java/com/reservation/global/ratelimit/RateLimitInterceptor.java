package com.reservation.global.ratelimit;

import com.reservation.global.common.constants.HeaderConstants;
import com.reservation.global.exception.GeneralException;
import com.reservation.global.exception.code.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userIdHeader = request.getHeader(HeaderConstants.USER_ID);
        if (userIdHeader == null) {
            return true;
        }

        Long userId;
        try {
            userId = Long.valueOf(userIdHeader);
        } catch (NumberFormatException e) {
            throw new GeneralException(ErrorCode.BAD_REQUEST);
        }
        if (!rateLimitService.isAllowed(userId)) {
            throw new GeneralException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }

        return true;
    }
}
