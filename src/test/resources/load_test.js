import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    // 1. Concurrent Execution & Idempotency Test
    idempotency_test: {
      executor: 'per-vu-iterations',
      vus: 5,               // 5 parallel Virtual Users
      iterations: 1,        // Each VU runs once simultaneously
      maxDuration: '10s',
      exec: 'testIdempotency',
    },
    // 2. Rate Limit Threshold Test
    rate_limit_test: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: 8,        // Fire 8 sequential requests to trigger 5-request capacity
      maxDuration: '10s',
      startTime: '3s',      // Start after idempotency scenario finishes
      exec: 'testRateLimit',
    },
  },
};

const BASE_URL = 'http://localhost:8080/api/orders';

// Test Scenario 1: Idempotency (Concurrent requests with identical key)
export function testIdempotency() {
  const payload = JSON.stringify({
    orderId: 'ORD-K6-100',
    customerId: 'CUST-ALPHA',
    amount: 199.99,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'X-Client-Id': 'k6-runner',
      'X-Idempotency-Key': 'IDEM-KEY-SHARED-99', // ALL VUs send the EXACT same key!
    },
  };

  const res = http.post(BASE_URL, payload, params);

  // Checks: Exactly ONE request acquires lock (or gets cached result if fast); others get 200 (cached) or 409 (conflict)
  check(res, {
    'Idempotency status is 200 or 409': (r) => r.status === 200 || r.status === 409,
    'Response has orderId when 200': (r) => r.status !== 200 || JSON.parse(r.body).orderId === 'ORD-K6-100',
  });
}

// Test Scenario 2: Rate Limiting (8 requests within 60s window, capacity = 5)
export function testRateLimit() {
  const iter = __ITER + 1; // k6 iteration index (1 to 8)

  const payload = JSON.stringify({
    orderId: `ORD-RATE-${iter}`,
    customerId: 'CUST-THROTTLE-USER', // Same customerId triggers @RateLimit key
    amount: 50.0,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'X-Client-Id': 'k6-runner',
      'X-Idempotency-Key': `IDEM-RATE-UNIQUE-${iter}`, // Unique idempotency key so idempotency doesn't interfere
    },
  };

  const res = http.post(BASE_URL, payload, params);

  if (iter <= 5) {
    check(res, {
      'Allowed request (<=5) returns 200': (r) => r.status === 200,
    });
  } else {
    check(res, {
      'Throttled request (>5) returns 429': (r) => r.status === 429,
      'Response error message present': (r) => JSON.parse(r.body).status === 429,
    });
  }

  sleep(0.1); // Small delay between requests
}