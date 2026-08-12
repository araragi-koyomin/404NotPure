import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const baseUrl = __ENV.BASE_URL || 'http://backend:8080';
const scenario = __ENV.SCENARIO || 'hot';
const requestRate = parsePositiveInteger(__ENV.RATE, 1);
const duration = __ENV.DURATION || '5s';
const preAllocatedVUs = parsePositiveInteger(__ENV.PRE_ALLOCATED_VUS, Math.max(2, requestRate));
const maxVUs = parsePositiveInteger(__ENV.MAX_VUS, Math.max(4, requestRate * 2));
const fixedMissingId = parsePositiveInteger(__ENV.MISSING_ID, 900000001);
const resultName = safeResultName(__ENV.RESULT_NAME || `${scenario}-${Date.now()}`);

const businessFailures = new Rate('business_failures');
const validResponses = new Counter('valid_business_responses');
const businessRequests = new Counter('business_requests');
const businessDuration = new Trend('business_duration', true);

export const options = {
  scenarios: {
    detail: {
      executor: 'constant-arrival-rate',
      rate: requestRate,
      timeUnit: '1s',
      duration,
      preAllocatedVUs,
      maxVUs,
      gracefulStop: '5s',
    },
  },
  thresholds: {
    checks: [{ threshold: 'rate==1', abortOnFail: true, delayAbortEval: '10s' }],
    business_failures: [{ threshold: 'rate==0', abortOnFail: true, delayAbortEval: '10s' }],
    http_req_failed: [{ threshold: 'rate==0', abortOnFail: true, delayAbortEval: '10s' }],
    dropped_iterations: [{ threshold: 'count==0', abortOnFail: true, delayAbortEval: '10s' }],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  noConnectionReuse: false,
  userAgent: '404NotPure-PERF-001',
};

export function setup() {
  const productIds = [];
  for (let page = 1; page <= 3; page += 1) {
    const response = http.get(`${baseUrl}/api/products/page?page=${page}&size=100&sort=id,asc`, {
      tags: { operation: 'dataset_discovery' },
    });
    const body = parseBody(response);
    if (response.status !== 200 || body.code !== '200' || !body.data || !Array.isArray(body.data.items)) {
      throw new Error(`DATA-001 discovery failed on page ${page}`);
    }
    body.data.items.forEach((item) => productIds.push(item.id));
  }
  if (productIds.length !== 300) {
    throw new Error(`Expected exactly 300 DATA-001 products, found ${productIds.length}`);
  }
  return { productIds };
}

export default function (data) {
  const expectedMissing = scenario === 'missing-fixed' || scenario === 'missing-random';
  let productId;
  if (scenario === 'missing-fixed') {
    productId = fixedMissingId;
  } else if (scenario === 'missing-random') {
    productId = fixedMissingId + resultOffset(resultName) + (__VU * 1000) + __ITER;
  } else if (scenario === 'hotspot') {
    productId = data.productIds[0];
  } else {
    productId = data.productIds[(__VU + __ITER) % data.productIds.length];
  }

  const response = http.get(`${baseUrl}/api/products/${productId}`, {
    tags: { operation: scenario },
  });
  const body = parseBody(response);
  const correct = expectedMissing
    ? response.status === 200 && body.code === '404' && body.data === null
    : response.status === 200 && body.code === '200' && body.data && body.data.id === productId;

  businessFailures.add(!correct);
  businessRequests.add(1);
  businessDuration.add(response.timings.duration);
  if (correct) {
    validResponses.add(1);
  }
  check(response, {
    'HTTP and business response are correct': () => correct,
  });
}

export function handleSummary(data) {
  const report = withoutSetupData(data);
  return {
    [`/results/${resultName}.json`]: JSON.stringify(report, null, 2),
    stdout: compactLine(data),
  };
}

function withoutSetupData(data) {
  const report = JSON.parse(JSON.stringify(data));
  delete report.setup_data;
  return report;
}

function compactLine(data) {
  const requests = metricValue(data, 'business_requests', 'count');
  const rate = metricValue(data, 'business_requests', 'rate');
  const failures = metricValue(data, 'business_failures', 'rate');
  const latency = data.metrics.business_duration ? data.metrics.business_duration.values : {};
  return JSON.stringify({
    scenario,
    requests,
    completedQps: rate,
    p50Ms: latency.med,
    p90Ms: latency['p(90)'],
    p95Ms: latency['p(95)'],
    p99Ms: latency['p(99)'],
    maxMs: latency.max,
    businessFailureRate: failures,
  });
}

function metricValue(data, metric, field) {
  return data.metrics[metric] && data.metrics[metric].values
    ? data.metrics[metric].values[field]
    : 0;
}

function parseBody(response) {
  try {
    return response.json();
  } catch (error) {
    return {};
  }
}

function parsePositiveInteger(value, fallback) {
  const parsed = Number.parseInt(value || '', 10);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
}

function safeResultName(value) {
  if (!/^[a-zA-Z0-9._-]+$/.test(value)) {
    throw new Error('RESULT_NAME contains unsupported characters');
  }
  return value;
}

function resultOffset(value) {
  let hash = 0;
  for (let index = 0; index < value.length; index += 1) {
    hash = ((hash * 31) + value.charCodeAt(index)) >>> 0;
  }
  return (hash % 500) * 1000000;
}
