import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

// 커스텀 메트릭
const bookingSuccess = new Counter('booking_success');
const bookingStockOut = new Counter('booking_stock_out');
const bookingFailed = new Counter('booking_failed');
const bookingDuration = new Trend('booking_duration', true);

const BASE_URL = __ENV.BASE_URL || 'http://localhost';

// 시나리오: 1000명이 동시에 예약 요청 (재고 10개)
export const options = {
    scenarios: {
        booking_spike: {
            executor: 'shared-iterations',
            vus: 100,
            iterations: 1000,
            maxDuration: '30s',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<2000'],
        booking_success: ['count<=10'],
    },
};

// 테스트 전 초기화: 사전에 생성된 사용자 ID 사용 (init.sql 기준 1~5)
export default function () {
    const userId = ((__VU - 1) * 10 + __ITER % 10) + 1;
    const idempotencyKey = uuidv4();

    const payload = JSON.stringify({
        productId: 1,
        payments: [
            {
                method: 'CREDIT_CARD',
                amount: 150000,
            },
        ],
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'X-User-Id': String(userId),
            'Idempotency-Key': idempotencyKey,
        },
    };

    const start = Date.now();
    const res = http.post(`${BASE_URL}/api/bookings`, payload, params);
    const duration = Date.now() - start;
    bookingDuration.add(duration);

    if (res.status === 200) {
        bookingSuccess.add(1);
        check(res, {
            'booking completed': (r) => {
                const body = JSON.parse(r.body);
                return body.orderStatus === 'COMPLETED';
            },
        });
    } else if (res.status === 409) {
        bookingStockOut.add(1);
        check(res, {
            'stock sold out': (r) => {
                const body = JSON.parse(r.body);
                return body.code === 'STOCK002';
            },
        });
    } else {
        bookingFailed.add(1);
    }
}
