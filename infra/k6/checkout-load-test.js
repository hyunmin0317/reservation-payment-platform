import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost';

// 시나리오: Checkout API TPS 측정
export const options = {
    scenarios: {
        checkout_load: {
            executor: 'constant-vus',
            vus: 50,
            duration: '10s',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<200', 'p(99)<500'],
    },
};

export default function () {
    const productId = ((__ITER % 5) + 1);
    const userId = ((__VU % 5) + 1);

    const params = {
        headers: {
            'X-User-Id': String(userId),
        },
    };

    const res = http.get(`${BASE_URL}/api/checkout/products/${productId}`, params);

    check(res, {
        'status is 200': (r) => r.status === 200,
        'has product name': (r) => {
            const body = JSON.parse(r.body);
            return body.productName !== undefined;
        },
    });
}
