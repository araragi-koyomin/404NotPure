import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const baseUrl = __ENV.BASE_URL || 'http://backend:8080';
const password = open('/run/secrets/perf_admin_password').trim();
const duration = __ENV.DURATION || '60s';
const readRate = positiveInteger(__ENV.READ_RATE, 100);
const writeRate = positiveInteger(__ENV.WRITE_RATE, 1);
const resultName = safeName(__ENV.RESULT_NAME || 'read-write');
const businessFailures = new Rate('business_failures');
const businessRequests = new Counter('business_requests');
const businessDuration = new Trend('business_duration', true);

if (!password) throw new Error('The performance-only admin password file is empty');

export const options = {
  scenarios: {
    readers: {
      executor: 'constant-arrival-rate', exec: 'readProduct', rate: readRate,
      timeUnit: '1s', duration, preAllocatedVUs: readRate, maxVUs: readRate * 2,
    },
    writer: {
      executor: 'constant-arrival-rate', exec: 'writeProduct', rate: writeRate,
      timeUnit: '1s', duration, preAllocatedVUs: writeRate, maxVUs: writeRate * 2,
    },
  },
  thresholds: {
    checks: [{ threshold: 'rate==1', abortOnFail: true, delayAbortEval: '10s' }],
    business_failures: [{ threshold: 'rate==0', abortOnFail: true, delayAbortEval: '10s' }],
    http_req_failed: [{ threshold: 'rate==0', abortOnFail: true, delayAbortEval: '10s' }],
    dropped_iterations: [{ threshold: 'count==0', abortOnFail: true, delayAbortEval: '10s' }],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  userAgent: '404NotPure-PERF-001',
};

export function setup() {
  const productResponse = http.get(`${baseUrl}/api/products/page?page=1&size=1&sort=id,asc`);
  const productBody = bodyOf(productResponse);
  if (productResponse.status !== 200 || productBody.code !== '200' || productBody.data.items.length !== 1) {
    throw new Error('Could not select the read/write product');
  }
  const id = productBody.data.items[0].id;
  const detailResponse = http.get(`${baseUrl}/api/products/${id}`);
  const detailBody = bodyOf(detailResponse);
  const loginResponse = http.post(`${baseUrl}/api/accounts/login`, JSON.stringify({
    username: 'demo_admin', password,
  }), { headers: { 'Content-Type': 'application/json' } });
  const loginBody = bodyOf(loginResponse);
  if (loginResponse.status !== 200 || loginBody.code !== '200' || !loginBody.data) {
    throw new Error('Runtime admin login failed');
  }
  return {
    id,
    originalDescription: detailBody.data.description,
    alternateDescription: `${detailBody.data.description} [PERF-001 temporary]`,
    token: loginBody.data,
  };
}

export function readProduct(data) {
  const response = http.get(`${baseUrl}/api/products/${data.id}`, { tags: { operation: 'read' } });
  const body = bodyOf(response);
  const description = body.data ? body.data.description : null;
  const correct = response.status === 200 && body.code === '200' && body.data
    && body.data.id === data.id
    && (description === data.originalDescription || description === data.alternateDescription);
  record(response, correct, 'read response is a valid committed value');
}

export function writeProduct(data) {
  const description = __ITER % 2 === 0 ? data.alternateDescription : data.originalDescription;
  const response = update(data.id, description, data.token);
  const body = bodyOf(response);
  const correct = response.status === 200 && body.code === '200';
  record(response, correct, 'admin update succeeds');
}

export function teardown(data) {
  const restore = update(data.id, data.originalDescription, data.token);
  const restoredBody = bodyOf(restore);
  if (restore.status !== 200 || restoredBody.code !== '200') {
    throw new Error('Could not restore the product after read/write testing');
  }
  const verify = http.get(`${baseUrl}/api/products/${data.id}`);
  const verifyBody = bodyOf(verify);
  if (verify.status !== 200 || verifyBody.code !== '200'
      || verifyBody.data.description !== data.originalDescription) {
    throw new Error('Restored database/cache value could not be verified');
  }
}

export function handleSummary(data) {
  const report = JSON.parse(JSON.stringify(data));
  delete report.setup_data;
  return {
    [`/results/${resultName}.json`]: JSON.stringify(report, null, 2),
    stdout: JSON.stringify({
      scenario: 'read-write', requests: value(data, 'business_requests', 'count'),
      completedQps: value(data, 'business_requests', 'rate'),
      p50Ms: value(data, 'business_duration', 'med'),
      p95Ms: value(data, 'business_duration', 'p(95)'),
      p99Ms: value(data, 'business_duration', 'p(99)'),
      maxMs: value(data, 'business_duration', 'max'),
      businessFailureRate: value(data, 'business_failures', 'rate'),
    }),
  };
}

function update(id, description, token) {
  return http.put(`${baseUrl}/api/products`, JSON.stringify({ id, description }), {
    headers: { 'Content-Type': 'application/json', token }, tags: { operation: 'write' },
  });
}

function record(response, correct, label) {
  businessFailures.add(!correct);
  businessRequests.add(1);
  businessDuration.add(response.timings.duration);
  check(response, { [label]: () => correct });
}

function bodyOf(response) { try { return response.json(); } catch (error) { return {}; } }
function value(data, metric, field) {
  return data.metrics[metric] && data.metrics[metric].values
    ? data.metrics[metric].values[field] : 0;
}
function positiveInteger(valueText, fallback) {
  const value = Number.parseInt(valueText || '', 10);
  return Number.isInteger(value) && value > 0 ? value : fallback;
}
function safeName(valueText) {
  if (!/^[a-zA-Z0-9._-]+$/.test(valueText)) throw new Error('Invalid RESULT_NAME');
  return valueText;
}
