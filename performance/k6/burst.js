import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const baseUrl = __ENV.BASE_URL || 'http://backend:8080';
const vus = positiveInteger(__ENV.BURST_VUS, 100);
const resultName = safeName(__ENV.RESULT_NAME || 'hotspot-burst');
const businessFailures = new Rate('business_failures');
const businessRequests = new Counter('business_requests');
const businessDuration = new Trend('business_duration', true);
const participatingVus = new Counter('participating_vus');

export const options = {
  scenarios: {
    burst: {
      executor: 'per-vu-iterations',
      vus,
      iterations: 1,
      maxDuration: '30s',
    },
  },
  thresholds: {
    checks: ['rate==1'],
    business_failures: ['rate==0'],
    http_req_failed: ['rate==0'],
    business_requests: [`count==${vus}`],
    participating_vus: [`count==${vus}`],
    iterations: [`count==${vus}`],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  userAgent: '404NotPure-PERF-001',
};

export function setup() {
  const response = http.get(`${baseUrl}/api/products/page?page=1&size=1&sort=id,asc`, {
    tags: { operation: 'dataset_discovery' },
  });
  const body = bodyOf(response);
  if (response.status !== 200 || body.code !== '200' || !body.data || body.data.items.length !== 1) {
    throw new Error('Could not select the hotspot product');
  }
  return { productId: body.data.items[0].id, startAt: Date.now() + 3000 };
}

export default function (data) {
  const waitMilliseconds = data.startAt - Date.now();
  if (waitMilliseconds > 0) sleep(waitMilliseconds / 1000);
  participatingVus.add(1);
  const response = http.get(`${baseUrl}/api/products/${data.productId}`, {
    tags: { operation: 'hotspot_burst' },
  });
  const body = bodyOf(response);
  const correct = response.status === 200 && body.code === '200'
    && body.data && body.data.id === data.productId;
  businessFailures.add(!correct);
  businessRequests.add(1);
  businessDuration.add(response.timings.duration);
  check(response, { 'hotspot response is correct': () => correct });
}

export function handleSummary(data) {
  const report = JSON.parse(JSON.stringify(data));
  delete report.setup_data;
  return {
    [`/results/${resultName}.json`]: JSON.stringify(report, null, 2),
    stdout: JSON.stringify({
      scenario: 'hotspot-burst',
      requests: value(data, 'business_requests', 'count'),
      completedQps: value(data, 'business_requests', 'rate'),
      p50Ms: value(data, 'business_duration', 'med'),
      p95Ms: value(data, 'business_duration', 'p(95)'),
      p99Ms: value(data, 'business_duration', 'p(99)'),
      maxMs: value(data, 'business_duration', 'max'),
      businessFailureRate: value(data, 'business_failures', 'rate'),
    }),
  };
}

function bodyOf(response) {
  try { return response.json(); } catch (error) { return {}; }
}

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
