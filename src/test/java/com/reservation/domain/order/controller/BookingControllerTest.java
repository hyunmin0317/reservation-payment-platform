package com.reservation.domain.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reservation.domain.order.dto.BookingRequest;
import com.reservation.domain.payment.dto.PaymentRequest;
import com.reservation.domain.payment.entity.PaymentMethod;
import com.reservation.global.common.constants.HeaderConstants;
import com.reservation.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class BookingControllerTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @DisplayName("X-User-Id 헤더 누락 시 400 응답")
    @Test
    void missingUserIdHeader() throws Exception {
        BookingRequest request = new BookingRequest(1L, List.of(
                new PaymentRequest(PaymentMethod.CREDIT_CARD, 100000)
        ));

        mockMvc.perform(post("/api/bookings")
                        .header(HeaderConstants.IDEMPOTENCY_KEY, "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @DisplayName("Idempotency-Key 헤더 누락 시 400 응답")
    @Test
    void missingIdempotencyKeyHeader() throws Exception {
        BookingRequest request = new BookingRequest(1L, List.of(
                new PaymentRequest(PaymentMethod.CREDIT_CARD, 100000)
        ));

        mockMvc.perform(post("/api/bookings")
                        .header(HeaderConstants.USER_ID, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @DisplayName("productId 누락 시 400 응답")
    @Test
    void missingProductId() throws Exception {
        String body = """
                {"payments": [{"method": "CREDIT_CARD", "amount": 100000}]}
                """;

        mockMvc.perform(post("/api/bookings")
                        .header(HeaderConstants.USER_ID, 1L)
                        .header(HeaderConstants.IDEMPOTENCY_KEY, "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @DisplayName("payments 빈 리스트 시 400 응답")
    @Test
    void emptyPayments() throws Exception {
        String body = """
                {"productId": 1, "payments": []}
                """;

        mockMvc.perform(post("/api/bookings")
                        .header(HeaderConstants.USER_ID, 1L)
                        .header(HeaderConstants.IDEMPOTENCY_KEY, "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
