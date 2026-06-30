import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const errorRate = new Rate('errors');

export const options = {
  stages: [
    { duration: '30s', target: 10 },
    { duration: '1m', target: 50 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],
    errors: ['rate<0.05'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  const payload = JSON.stringify({
    sender: '0x4F2E1234567890ABCDEF1234567890ABCDEF8B9C',
    nonce: '0x01',
    callData: '0x',
    maxFeePerGas: '0x5F5E100',
    maxPriorityFeePerGas: '0x5F5E100',
    mdaoMaxAmount: '0xDE0B6B3A7640000',
    usdtMaxAmount: '0x989680',
  });

  const res = http.post(`${BASE_URL}/v1/sign`, payload, {
    headers: { 'Content-Type': 'application/json', 'X-API-Key': 'test-key' },
  });

  const ok = check(res, { 'status is 200 or 429': (r) => r.status === 200 || r.status === 429 });
  errorRate.add(!ok);
  sleep(0.1);
}
