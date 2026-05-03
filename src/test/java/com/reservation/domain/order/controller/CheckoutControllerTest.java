package com.reservation.domain.order.controller;

import com.reservation.domain.product.entity.Product;
import com.reservation.domain.product.repository.ProductRepository;
import com.reservation.domain.stock.entity.Stock;
import com.reservation.domain.stock.repository.StockRepository;
import com.reservation.domain.stock.service.RedisStockService;
import com.reservation.domain.user.entity.User;
import com.reservation.domain.user.repository.UserRepository;
import com.reservation.global.common.constants.HeaderConstants;
import com.reservation.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CheckoutControllerTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RedisStockService redisStockService;

    private Product product;
    private User user;

    @BeforeEach
    void setUp() {
        stockRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();

        product = productRepository.saveAndFlush(Product.create(
                "테스트 숙소", 100000,
                LocalTime.of(15, 0), LocalTime.of(11, 0),
                "테스트 설명"
        ));
        stockRepository.saveAndFlush(Stock.create(product, 10));
        redisStockService.initStock(product.getId(), 10);
        user = userRepository.saveAndFlush(User.create("테스트유저", "test@test.com", 50000));
    }

    @DisplayName("X-User-Id 헤더 누락 시 400 응답")
    @Test
    void missingUserIdHeader() throws Exception {
        mockMvc.perform(get("/api/checkout/products/" + product.getId()))
                .andExpect(status().isBadRequest());
    }

    @DisplayName("존재하지 않는 상품 요청 시 404 응답")
    @Test
    void productNotFound() throws Exception {
        mockMvc.perform(get("/api/checkout/products/999999")
                        .header(HeaderConstants.USER_ID, user.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT001"));
    }

    @DisplayName("정상 조회 시 200 응답")
    @Test
    void successfulCheckout() throws Exception {
        mockMvc.perform(get("/api/checkout/products/" + product.getId())
                        .header(HeaderConstants.USER_ID, user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productName").value("테스트 숙소"))
                .andExpect(jsonPath("$.remainingStock").value(10))
                .andExpect(jsonPath("$.userPoint").value(50000));
    }
}
