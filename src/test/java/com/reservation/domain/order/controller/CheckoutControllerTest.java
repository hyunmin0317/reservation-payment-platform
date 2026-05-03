package com.reservation.domain.order.controller;

import com.reservation.domain.order.dto.CheckoutResponse;
import com.reservation.domain.order.service.CheckoutService;
import com.reservation.global.common.constants.HeaderConstants;
import com.reservation.global.exception.GeneralException;
import com.reservation.global.exception.code.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CheckoutController.class)
class CheckoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CheckoutService checkoutService;

    @DisplayName("X-User-Id 헤더 누락 시 400 응답")
    @Test
    void missingUserIdHeader() throws Exception {
        mockMvc.perform(get("/api/checkout/products/1"))
                .andExpect(status().isBadRequest());
    }

    @DisplayName("존재하지 않는 상품 요청 시 404 응답")
    @Test
    void productNotFound() throws Exception {
        when(checkoutService.getCheckout(999L, 1L))
                .thenThrow(new GeneralException(ErrorCode.PRODUCT_NOT_FOUND));

        mockMvc.perform(get("/api/checkout/products/999")
                        .header(HeaderConstants.USER_ID, 1L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT001"));
    }

    @DisplayName("정상 조회 시 200 응답")
    @Test
    void successfulCheckout() throws Exception {
        when(checkoutService.getCheckout(1L, 1L))
                .thenReturn(new CheckoutResponse(
                        1L, "테스트 숙소", 100000,
                        LocalTime.of(15, 0), LocalTime.of(11, 0),
                        "테스트 설명", 10, LocalTime.MIDNIGHT, 50000
                ));

        mockMvc.perform(get("/api/checkout/products/1")
                        .header(HeaderConstants.USER_ID, 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productName").value("테스트 숙소"))
                .andExpect(jsonPath("$.remainingStock").value(10))
                .andExpect(jsonPath("$.userPoint").value(50000));
    }
}
