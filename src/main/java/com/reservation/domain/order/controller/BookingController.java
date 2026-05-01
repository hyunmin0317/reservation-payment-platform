package com.reservation.domain.order.controller;

import com.reservation.domain.order.dto.BookingRequest;
import com.reservation.domain.order.dto.BookingResponse;
import com.reservation.domain.order.service.BookingService;
import com.reservation.global.common.constants.HeaderConstants;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<BookingResponse> book(
            @RequestHeader(HeaderConstants.USER_ID) Long userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody BookingRequest request) {
        BookingResponse response = bookingService.book(userId, idempotencyKey, request);
        return ResponseEntity.ok(response);
    }
}