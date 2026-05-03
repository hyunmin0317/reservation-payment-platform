package com.reservation.domain.order.controller;

import com.reservation.domain.order.dto.BookingRequest;
import com.reservation.domain.order.dto.BookingResponse;
import com.reservation.domain.order.service.BookingService;
import com.reservation.global.common.constants.HeaderConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Booking", description = "예약 결제 API")
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @Operation(summary = "예약 결제", description = "상품을 예약하고 결제를 진행한다. 멱등성 키를 통해 중복 요청을 방지한다.")
    @ApiResponse(responseCode = "200", description = "예약 결제 성공")
    @ApiResponse(responseCode = "400", description = "잘못된 요청 (결제 수단 조합 오류, 포인트 부족 등)")
    @ApiResponse(responseCode = "409", description = "재고 부족 또는 중복 요청")
    @ApiResponse(responseCode = "503", description = "결제 서비스 일시 불가")
    @PostMapping
    public ResponseEntity<BookingResponse> book(
            @Parameter(description = "사용자 ID", required = true) @RequestHeader(HeaderConstants.USER_ID) Long userId,
            @Parameter(description = "멱등성 키 (중복 요청 방지)", required = true) @RequestHeader(HeaderConstants.IDEMPOTENCY_KEY) @Size(max = 50) String idempotencyKey,
            @Valid @RequestBody BookingRequest request) {
        BookingResponse response = bookingService.book(userId, idempotencyKey, request);
        return ResponseEntity.ok(response);
    }
}
