import http from 'k6/http';
import { hmac } from 'k6/crypto';
import encoding from 'k6/encoding';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL;
const JWT_SECRET = __ENV.JWT_SECRET;
const USER_IDS = __ENV.USER_IDS.split(',');
const PEAK_VUS = Number(__ENV.PEAK_VUS || 60);
const RAMP = __ENV.RAMP || '2m';
const HOLD = __ENV.HOLD || '3m';
const LANGUAGE = __ENV.LANGUAGE || 'JAVA';

const POLL_INTERVAL_SECONDS = 1;
const POLL_LIMIT = 180;
const THINK_SECONDS = 2;
const REJECTED_WAIT_SECONDS = Number(__ENV.REJECTED_WAIT_SECONDS || 2);

const SOURCE_CODES = {
  JAVA: 'class Solution { public int solution(int[] numbers) { int count = 0; for (int number : numbers) if (number % 2 == 0) count++; return count; } }',
  JAVASCRIPT: 'function solution(numbers) { return numbers.filter((number) => number % 2 === 0).length; }',
};

const judgeTurnaround = new Trend('judge_turnaround', true);
const submissionRejected = new Rate('submission_rejected');
const judgeNotAccepted = new Counter('judge_not_accepted');
const judgeTimedOut = new Counter('judge_timed_out');

if (PEAK_VUS > USER_IDS.length) {
  throw new Error(`PEAK_VUS(${PEAK_VUS})가 테스트 유저 수(${USER_IDS.length})보다 많습니다. users.sql 의 유저를 늘리세요`);
}

export const options = {
  scenarios: {
    submissions: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: RAMP, target: PEAK_VUS },
        { duration: HOLD, target: PEAK_VUS },
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '3m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:POST /api/problems/1/submissions}': ['p(95)<1000'],
    judge_turnaround: ['p(95)<60000'],
    judge_not_accepted: ['count==0'],
    judge_timed_out: ['count==0'],
  },
};

http.setResponseCallback(http.expectedStatuses(200, 202, 429));

function accessToken(userId) {
  const now = Math.floor(Date.now() / 1000);
  const encode = (value) => encoding.b64encode(JSON.stringify(value), 'rawurl');
  const body = `${encode({ alg: 'HS256' })}.${encode({ sub: userId, iat: now, exp: now + 3600 })}`;
  return `${body}.${hmac('sha256', JWT_SECRET, body, 'base64rawurl')}`;
}

export default function () {
  const headers = {
    Authorization: `Bearer ${accessToken(USER_IDS[__VU - 1])}`,
    'Content-Type': 'application/json',
  };

  const accepted = http.post(
    `${BASE_URL}/api/problems/1/submissions`,
    JSON.stringify({ language: LANGUAGE, sourceCode: SOURCE_CODES[LANGUAGE] }),
    { headers, tags: { name: 'POST /api/problems/1/submissions' } },
  );
  submissionRejected.add(accepted.status === 429);
  if (accepted.status !== 202) {
    sleep(REJECTED_WAIT_SECONDS * (0.5 + Math.random()));
    return;
  }

  const acceptedAt = Date.now();
  const { id } = accepted.json();
  for (let attempt = 0; attempt < POLL_LIMIT; attempt++) {
    sleep(POLL_INTERVAL_SECONDS);
    const result = http.get(`${BASE_URL}/api/submissions/${id}`, { headers, tags: { name: 'GET /api/submissions/{id}' } }).json();
    if (result.status === 'DONE' || result.status === 'FAILED') {
      judgeTurnaround.add(Date.now() - acceptedAt);
      if (!check(result, { '정답(AC)으로 채점됨': (value) => value.verdict === 'AC' })) {
        judgeNotAccepted.add(1);
      }
      sleep(THINK_SECONDS);
      return;
    }
  }
  judgeTimedOut.add(1);
}
