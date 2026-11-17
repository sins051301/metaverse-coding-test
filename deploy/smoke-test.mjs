// 운영 구성(compose.prod.yaml)을 띄운 뒤 실제로 제출이 채점되는지 확인한다. CI(.github/workflows/ci.yml)와 로컬 점검에서 쓴다.
// 로그인은 Google 을 거칠 수 없으므로, 점검용 DB 에 만든 유저 id 로 서버와 같은 JWT_SECRET 을 써서 토큰을 직접 만든다.
// 사용: BASE_URL=http://localhost JWT_SECRET=... USER_ID=1 node deploy/smoke-test.mjs
import { createHmac } from 'node:crypto';

const { BASE_URL, JWT_SECRET, USER_ID } = process.env;
const POLL_INTERVAL_MS = 1000;
const POLL_LIMIT = 90;

function accessToken() {
  const encode = (value) => Buffer.from(JSON.stringify(value)).toString('base64url');
  const now = Math.floor(Date.now() / 1000);
  const body = `${encode({ alg: 'HS256' })}.${encode({ sub: USER_ID, iat: now, exp: now + 600 })}`;
  return `${body}.${createHmac('sha256', JWT_SECRET).update(body).digest('base64url')}`;
}

const headers = { cookie: `accessToken=${accessToken()}`, 'content-type': 'application/json', origin: BASE_URL };

// 문제 1 (짝수 개수 세기)의 정답
const SUBMISSIONS = {
  JAVA: 'class Solution { public int solution(int[] numbers) { int count = 0; for (int number : numbers) if (number % 2 == 0) count++; return count; } }',
  JAVASCRIPT: 'function solution(numbers) { return numbers.filter((number) => number % 2 === 0).length; }',
};

async function judge(language, sourceCode) {
  const accepted = await fetch(`${BASE_URL}/api/problems/1/submissions`, { method: 'POST', headers, body: JSON.stringify({ language, sourceCode }) });
  if (accepted.status !== 202) {
    throw new Error(`${language} 제출 접수 실패: ${accepted.status} ${await accepted.text()}`);
  }
  const { id } = await accepted.json();
  for (let attempt = 0; attempt < POLL_LIMIT; attempt++) {
    await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS));
    const result = await (await fetch(`${BASE_URL}/api/submissions/${id}`, { headers })).json();
    if (result.status === 'DONE' || result.status === 'FAILED') {
      return result;
    }
  }
  throw new Error(`${language} 채점이 ${POLL_LIMIT}초 안에 끝나지 않았습니다`);
}

let failed = false;
for (const [language, sourceCode] of Object.entries(SUBMISSIONS)) {
  const result = await judge(language, sourceCode);
  console.log(`${language}: ${result.status} ${result.verdict} ${result.score}`);
  if (result.verdict !== 'AC') {
    failed = true;
  }
}

const checks = [
  ['GET /', `${BASE_URL}/`, 200],
  ['GET /problems/1 (SPA 경로)', `${BASE_URL}/problems/1`, 200],
  ['GET /api/problems', `${BASE_URL}/api/problems`, 200],
];
for (const [name, url, expected] of checks) {
  const status = (await fetch(url, { redirect: 'manual' })).status;
  console.log(`${name}: ${status}`);
  if (status !== expected) {
    failed = true;
  }
}

if (failed) {
  console.error('운영 구성 점검 실패');
  process.exit(1);
}
console.log('운영 구성 점검 통과');
