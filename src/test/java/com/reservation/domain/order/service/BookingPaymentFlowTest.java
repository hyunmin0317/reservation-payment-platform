package com.reservation.domain.order.service;

import com.reservation.domain.order.dto.BookingRequest;
import com.reservation.domain.order.dto.BookingResponse;
import com.reservation.domain.order.entity.OrderStatus;
import com.reservation.domain.order.repository.OrderRepository;
import com.reservation.domain.payment.dto.PaymentRequest;
import com.reservation.domain.payment.entity.PaymentMethod;
import com.reservation.domain.payment.entity.PaymentStatus;
import com.reservation.domain.payment.repository.PaymentRepository;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingPaymentFlowTest extends IntegrationTestSupport {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RedisStockService redisStockService;

    private User user;
    private Product product;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        stockRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();

        user = userRepository.saveAndFlush(User.create("테스트유저", "test@test.com", 50000));
        product = productRepository.saveAndFlush(Product.create(
                "테스트 숙소", 100000,
                LocalTime.of(15, 0), LocalTime.of(11, 0),
                "결제 플로우 테스트용 상품"
        ));
        stockRepository.saveAndFlush(Stock.create(product, 10));
        redisStockService.initStock(product.getId(), 10);
    }

    @Nested
    @DisplayName("정상 결제 플로우")
    class SuccessFlow {

        @DisplayName("신용카드 단일 결제로 예약 성공")
        @Test
        void creditCardBookingSucceeds() {
            BookingResponse response = book(PaymentMethod.CREDIT_CARD, 100000);

            assertThat(response.orderStatus()).isEqualTo(OrderStatus.COMPLETED);
            assertThat(response.totalAmount()).isEqualTo(100000);
            assertThat(response.payments()).hasSize(1);
            assertThat(response.payments().get(0).status()).isEqualTo(PaymentStatus.APPROVED);
        }

        @DisplayName("Y페이 단일 결제로 예약 성공")
        @Test
        void yPayBookingSucceeds() {
            BookingResponse response = book(PaymentMethod.Y_PAY, 100000);

            assertThat(response.orderStatus()).isEqualTo(OrderStatus.COMPLETED);
            assertThat(response.payments()).hasSize(1);
            assertThat(response.payments().get(0).method()).isEqualTo(PaymentMethod.Y_PAY);
        }

        @DisplayName("신용카드 + Y포인트 복합 결제로 예약 성공")
        @Test
        void creditCardWithPointSucceeds() {
            BookingRequest request = new BookingRequest(product.getId(), List.of(
                    new PaymentRequest(PaymentMethod.Y_POINT, 30000),
                    new PaymentRequest(PaymentMethod.CREDIT_CARD, 70000)
            ));

            BookingResponse response = bookingService.book(user.getId(), UUID.randomUUID().toString(), request);

            assertThat(response.orderStatus()).isEqualTo(OrderStatus.COMPLETED);
            assertThat(response.payments()).hasSize(2);
            User updatedUser = userRepository.findById(user.getId()).orElseThrow();
            assertThat(updatedUser.getPointBalance()).isEqualTo(20000);
        }

        @DisplayName("Y페이 + Y포인트 복합 결제로 예약 성공")
        @Test
        void yPayWithPointSucceeds() {
            BookingRequest request = new BookingRequest(product.getId(), List.of(
                    new PaymentRequest(PaymentMethod.Y_POINT, 20000),
                    new PaymentRequest(PaymentMethod.Y_PAY, 80000)
            ));

            BookingResponse response = bookingService.book(user.getId(), UUID.randomUUID().toString(), request);

            assertThat(response.orderStatus()).isEqualTo(OrderStatus.COMPLETED);
            assertThat(response.payments()).hasSize(2);
            User updatedUser = userRepository.findById(user.getId()).orElseThrow();
            assertThat(updatedUser.getPointBalance()).isEqualTo(30000);
        }
    }

    @Nested
    @DisplayName("결제 실패")
    class FailureFlow {

        @DisplayName("포인트 부족 시 예약 실패")
        @Test
        void insufficientPointsFails() {
            assertThatThrownBy(() -> book(PaymentMethod.Y_POINT, 100000))
                    .isInstanceOf(GeneralException.class)
                    .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.INSUFFICIENT_POINTS));
        }

        @DisplayName("신용카드 + Y페이 혼용 시 거절")
        @Test
        void invalidCombinationRejected() {
            BookingRequest request = new BookingRequest(product.getId(), List.of(
                    new PaymentRequest(PaymentMethod.CREDIT_CARD, 50000),
                    new PaymentRequest(PaymentMethod.Y_PAY, 50000)
            ));

            assertThatThrownBy(() -> bookingService.book(user.getId(), UUID.randomUUID().toString(), request))
                    .isInstanceOf(GeneralException.class)
                    .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_PAYMENT_COMBINATION));
        }

        @DisplayName("재고 소진 후 예약 실패")
        @Test
        void stockExhaustedFails() {
            redisStockService.initStock(product.getId(), 1);
            book(PaymentMethod.CREDIT_CARD, 100000);

            assertThatThrownBy(() -> book(PaymentMethod.CREDIT_CARD, 100000))
                    .isInstanceOf(GeneralException.class)
                    .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.STOCK_SOLD_OUT));
        }
    }

    private BookingResponse book(PaymentMethod method, int amount) {
        BookingRequest request = new BookingRequest(product.getId(), List.of(
                new PaymentRequest(method, amount)
        ));
        return bookingService.book(user.getId(), UUID.randomUUID().toString(), request);
    }
}
