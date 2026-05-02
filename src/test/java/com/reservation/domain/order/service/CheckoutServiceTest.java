package com.reservation.domain.order.service;

import com.reservation.domain.order.dto.CheckoutResponse;
import com.reservation.domain.product.entity.Product;
import com.reservation.domain.product.repository.ProductRepository;
import com.reservation.domain.stock.entity.Stock;
import com.reservation.domain.stock.repository.StockRepository;
import com.reservation.domain.stock.service.RedisStockService;
import com.reservation.domain.user.entity.User;
import com.reservation.domain.user.repository.UserRepository;
import com.reservation.global.exception.GeneralException;
import com.reservation.global.exception.code.ErrorCode;
import com.reservation.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckoutServiceTest extends IntegrationTestSupport {

    @Autowired
    private CheckoutService checkoutService;

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
                "테스트 숙소", 150000,
                LocalTime.of(15, 0), LocalTime.of(11, 0),
                "체크아웃 테스트용 상품"
        ));
        stockRepository.saveAndFlush(Stock.create(product, 10));
        redisStockService.initStock(product.getId(), 10);

        user = userRepository.saveAndFlush(User.create("테스트유저", "test@test.com", 50000));
    }

    @DisplayName("상품 정보, 잔여 재고, 사용자 포인트를 정상 조회한다")
    @Test
    void checkoutReturnsProductInfoAndStock() {
        CheckoutResponse response = checkoutService.getCheckout(product.getId(), user.getId());

        assertThat(response.productId()).isEqualTo(product.getId());
        assertThat(response.productName()).isEqualTo("테스트 숙소");
        assertThat(response.price()).isEqualTo(150000);
        assertThat(response.remainingStock()).isEqualTo(10);
        assertThat(response.userPoint()).isEqualTo(50000);
    }

    @DisplayName("존재하지 않는 상품 ID로 조회 시 PRODUCT_NOT_FOUND 예외")
    @Test
    void throwsWhenProductNotFound() {
        assertThatThrownBy(() -> checkoutService.getCheckout(999L, user.getId()))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
    }

    @DisplayName("존재하지 않는 사용자 ID로 조회 시 USER_NOT_FOUND 예외")
    @Test
    void throwsWhenUserNotFound() {
        assertThatThrownBy(() -> checkoutService.getCheckout(product.getId(), 999L))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }
}