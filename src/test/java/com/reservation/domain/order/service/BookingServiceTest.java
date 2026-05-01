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

class BookingServiceTest extends IntegrationTestSupport {

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
                "통합 테스트용 상품"
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
            BookingRequest request = new BookingRequest(product.getId(), List.of(
                    new PaymentRequest(PaymentMethod.CREDIT_CARD, 100000)
            ));

            BookingResponse response = bookingService.book(user.getId(), UUID.randomUUID().toString(), request);

            assertThat(response.orderStatus()).isEqualTo(OrderStatus.COMPLETED);
            assertThat(response.totalAmount()).isEqualTo(100000);
            assertThat(response.payments()).hasSize(1);
            assertThat(response.payments().get(0).status()).isEqualTo(PaymentStatus.APPROVED);
        }

        @DisplayName("신용카드 + Y포인트 복합 결제로 예약 성공")
        @Test
        void compositeBookingSucceeds() {
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
    }

    @Nested
    @DisplayName("결제 실패 시 보상 트랜잭션")
    class FailureCompensation {

        @DisplayName("포인트 부족 시 예약 실패 및 재고 복구")
        @Test
        void insufficientPointsRestoresStock() {
            BookingRequest request = new BookingRequest(product.getId(), List.of(
                    new PaymentRequest(PaymentMethod.Y_POINT, 60000)
            ));

            assertThatThrownBy(() -> bookingService.book(user.getId(), UUID.randomUUID().toString(), request))
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
    }

    @Nested
    @DisplayName("멱등성 처리")
    class Idempotency {

        @DisplayName("동일 멱등성 키로 재요청 시 기존 결과 반환")
        @Test
        void duplicateRequestReturnsSameResult() {
            String idempotencyKey = UUID.randomUUID().toString();
            BookingRequest request = new BookingRequest(product.getId(), List.of(
                    new PaymentRequest(PaymentMethod.CREDIT_CARD, 100000)
            ));

            BookingResponse first = bookingService.book(user.getId(), idempotencyKey, request);
            BookingResponse second = bookingService.book(user.getId(), idempotencyKey, request);

            assertThat(second.orderId()).isEqualTo(first.orderId());
            assertThat(second.orderNumber()).isEqualTo(first.orderNumber());
        }

        @DisplayName("다른 멱등성 키로 요청 시 별도 주문 생성")
        @Test
        void differentKeysCreateSeparateOrders() {
            BookingRequest request = new BookingRequest(product.getId(), List.of(
                    new PaymentRequest(PaymentMethod.CREDIT_CARD, 100000)
            ));

            BookingResponse first = bookingService.book(user.getId(), UUID.randomUUID().toString(), request);
            BookingResponse second = bookingService.book(user.getId(), UUID.randomUUID().toString(), request);

            assertThat(second.orderId()).isNotEqualTo(first.orderId());
        }
    }

    @Nested
    @DisplayName("재고 소진")
    class StockExhaustion {

        @DisplayName("재고 소진 후 요청 시 실패")
        @Test
        void bookingFailsWhenStockExhausted() {
            redisStockService.initStock(product.getId(), 1);

            BookingRequest request = new BookingRequest(product.getId(), List.of(
                    new PaymentRequest(PaymentMethod.CREDIT_CARD, 100000)
            ));

            bookingService.book(user.getId(), UUID.randomUUID().toString(), request);

            assertThatThrownBy(() -> bookingService.book(user.getId(), UUID.randomUUID().toString(), request))
                    .isInstanceOf(GeneralException.class)
                    .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.STOCK_SOLD_OUT));
        }
    }
}
