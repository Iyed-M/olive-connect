import http from 'k6/http'
import { check, sleep } from 'k6'

const BASE_URL = (__ENV.BASE_URL || __ENV.K6_BASE_URL || 'http://localhost').replace(/\/+$/, '')
const LOAD_EMAIL = __ENV.K6_EMAIL
const LOAD_PASSWORD = __ENV.K6_PASSWORD
const VUS = Number(__ENV.K6_VUS || 100)
const RAMP_DURATION = __ENV.K6_RAMP_DURATION || '2m'
const HOLD_DURATION = __ENV.K6_HOLD_DURATION || '6m'
const DOWN_DURATION = __ENV.K6_DOWN_DURATION || '2m'

export const options = {
  scenarios: {
    harvest_peak: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: RAMP_DURATION, target: VUS },
        { duration: HOLD_DURATION, target: VUS },
        { duration: DOWN_DURATION, target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    'http_req_duration{name:map}': ['p(95)<2000', 'p(99)<10000'],
    http_req_failed: ['rate<0.01'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
}

function randomBboxAroundSfax() {
  const west = 10.6 + Math.random() * 0.15
  const south = 34.7 + Math.random() * 0.1
  return [west, south, west + 0.15, south + 0.1].join(',')
}

function login() {
  if (!LOAD_EMAIL || !LOAD_PASSWORD) return null

  const response = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email: LOAD_EMAIL, password: LOAD_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } },
  )
  check(response, { 'load account can log in': (candidate) => candidate.status === 200 })
  if (response.status !== 200) return null
  return response.json('accessToken')
}

export function setup() {
  return { accessToken: login() }
}

export default function ({ accessToken }) {
  const map = http.get(`${BASE_URL}/api/map/public?bbox=${encodeURIComponent(randomBboxAroundSfax())}&zoom=11`, {
    tags: { name: 'map' },
  })
  check(map, { 'public map is available': (response) => response.status === 200 })

  if (accessToken) {
    const requests = http.get(`${BASE_URL}/api/harvest-requests?mine=true`, {
      headers: { Authorization: `Bearer ${accessToken}` },
      tags: { name: 'myRequests' },
    })
    check(requests, { 'my harvest requests are available': (response) => response.status === 200 })
  } else {
    const status = http.get(`${BASE_URL}/api/system/status`, { tags: { name: 'systemStatus' } })
    check(status, { 'system status is available': (response) => response.status === 200 })
  }

  sleep(Math.random() * 3)
}

export function handleSummary(data) {
  const outputFile = __ENV.K6_SUMMARY_FILE || 'load-tests/k6-summary.json'
  return {
    [outputFile]: JSON.stringify(data, null, 2),
    stdout: `k6 summary written to ${outputFile}\n`,
  }
}
