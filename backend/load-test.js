import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const errorRate = new Rate('errors');

export const options = {
  stages: [
    { duration: '10s', target: 5 },   // ramp up
    { duration: '30s', target: 5 },   // steady 5 req/s
    { duration: '10s', target: 20 },  // burst
    { duration: '10s', target: 0 },   // cooldown
  ],
  thresholds: {
    errors: ['rate<0.1'], // <10% errors
    http_req_duration: ['p(95)<3000'], // 95% under 3s
  },
};

const BASE_URL = __ENV.BACKEND_URL || 'http://localhost:8080';

const senderAddresses = [
  '0x1111111111111111111111111111111111111111',
  '0x2222222222222222222222222222222222222222',
  '0x3333333333333333333333333333333333333333',
];

function randomSender() {
  return senderAddresses[Math.floor(Math.random() * senderAddresses.length)];
}

export default function () {
  const sender = randomSender();

  const payload = JSON.stringify({
    sender: sender,
    nonce: '0x1',
    initCode: '0x',
    callData: '0x',
    verificationGasLimit: '0x249f0',
    callGasLimit: '0x30d40',
    preVerificationGas: '0xc350',
    maxPriorityFeePerGas: '0x0',
    maxFeePerGas: '0x0',
    paymasterAndData: '0x',
    mdaoMaxAmount: '0xde0b6b3a7640000',
    usdtMaxAmount: null,
  });

  const params = {
    headers: { 'Content-Type': 'application/json' },
  };

  const res = http.post(`${BASE_URL}/sign`, payload, params);

  const isSuccess = check(res, {
    'status is 200 or 429': (r) => r.status === 200 || r.status === 429,
    'response time < 5s': (r) => r.timings.duration < 5000,
  });

  errorRate.add(!isSuccess);

  // Respect rate limits: max 1 req/30s per sender
  if (res.status === 429) {
    sleep(30);
  } else {
    sleep(Math.random() * 2 + 1); // 1-3s between requests
  }
}

// k6 run load-test.js --vus 10 --duration 60s
